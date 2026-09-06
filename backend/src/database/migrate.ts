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
      if (appliedSet.has(file)) {
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
