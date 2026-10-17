import { readdirSync, readFileSync } from 'fs';
import { join } from 'path';
import { Pool } from 'pg';

/**
 * Minimal, dependency-light migration runner.
 *
 * Design intentionally: plain numbered .sql files run once each, tracked in
 * a `schema_migrations` table. No ORM magic — every migration is inspectable
 * SQL. This keeps schema evolution auditable, which matters for a private
 * messenger. Run with: `npm run migrate`.
 */
/**
 * Merged-project renumbering: these files carried the (colliding) names on the right while the four
 * feature branches were separate. A database that already applied one under its old name must not
 * run it again under the new name.
 */
const FORMER_NAMES: Record<string, string> = {
  '024_phase2a_voice_waveform.sql': '021_phase2a_voice_waveform.sql',
  '025_phase2a_polls.sql': '022_phase2a_polls.sql',
  '026_phase2a_scheduled_messages.sql': '023_phase2a_scheduled_messages.sql',
  '027_phase2a_group_governance.sql': '024_phase2a_group_governance.sql',
  '028_phase2a_saved_messages.sql': '025_phase2a_saved_messages.sql',
  '029_phase2_edit_history.sql': '026_phase2_edit_history.sql',
  '030_phase3a_counters.sql': '021_phase3a_counters.sql',
  '031_phase3a_canvas.sql': '022_phase3a_canvas.sql',
  '032_phase3b_stories.sql': '023_phase3b_stories.sql',
  '033_phase3_vault.sql': '024_phase3_vault.sql',
  '034_phase3a_quizzes.sql': '025_phase3a_quizzes.sql',
  '035_phase3_counter_theme.sql': '026_phase3_counter_theme.sql',
  '036_phase3_letter_unlock_reminder.sql': '027_phase3_letter_unlock_reminder.sql',
  '037_phase3_album_photos.sql': '028_phase3_album_photos.sql',
  '038_phase4_privacy_disappearing.sql': '021_phase13_privacy_disappearing.sql',
  '039_phase4_two_step.sql': '022_phase13_two_step.sql',
};

async function run(): Promise<void> {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) {
    throw new Error('DATABASE_URL is not set');
  }

  const pool = new Pool({ connectionString });
  const client = await pool.connect();

  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        filename    TEXT PRIMARY KEY,
        applied_at  TIMESTAMPTZ NOT NULL DEFAULT now()
      );
    `);

    const migrationsDir = join(__dirname, 'migrations');
    const files = readdirSync(migrationsDir)
      .filter((f) => f.endsWith('.sql'))
      .sort();

    const { rows: applied } = await client.query<{ filename: string }>(
      'SELECT filename FROM schema_migrations',
    );
    const appliedSet = new Set(applied.map((r) => r.filename));

    for (const file of files) {
      const former = FORMER_NAMES[file];
      if (appliedSet.has(file) || (former !== undefined && appliedSet.has(former))) {
        // eslint-disable-next-line no-console
        console.log(`skip (already applied): ${file}`);
        continue;
      }

      const sql = readFileSync(join(migrationsDir, file), 'utf-8');
      // eslint-disable-next-line no-console
      console.log(`applying: ${file}`);

      await client.query('BEGIN');
      try {
        await client.query(sql);
        await client.query('INSERT INTO schema_migrations (filename) VALUES ($1)', [file]);
        await client.query('COMMIT');
      } catch (err) {
        await client.query('ROLLBACK');
        throw new Error(`Migration ${file} failed: ${(err as Error).message}`);
      }
    }

    // eslint-disable-next-line no-console
    console.log('Migrations complete.');
  } finally {
    client.release();
    await pool.end();
  }
}

run().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(err);
  process.exit(1);
});
