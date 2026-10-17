# Phase 8C — Chat screen redesign

- **Top bar:** brand-gradient hero with white text/icons, white ring around the avatar, green online dot.
- **Bubbles:** received = crisp white card (light) / lifted plum surface (dark) with hairline border + soft shadow; own = brand gradient with a coloured glow shadow.
- **Grouping:** consecutive messages from the same sender within 3 minutes (same day) get tight spacing, and only the last bubble of the group has the tail corner. In groups, the sender name shows once per run.
- **Footer inside the bubble:** "edited" label, time (Jalali-locale digits, `ChatDateFormatter.time`) and the receipt icon (read ticks are light blue).
- **Date separators:** frosted-glass pill with a hairline.
- **Composer:** floating rounded pill with shadow holding emoji/attach/field/mic/send; transparent borderless field, grows up to 5 lines.
