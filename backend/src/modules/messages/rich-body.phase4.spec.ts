import { describeRichBody } from './rich-body';

describe('describeRichBody (phase 3/4 markers)', () => {
  it('hides surprise text from notifications', () => {
    expect(describeRichBody('surprise:v1:Happy%20birthday')).toBe('[surprise]');
  });

  it('labels end-to-end encrypted bodies without exposing them', () => {
    expect(describeRichBody('e2ee:v2:AAAA_-bb.CCCC.DDDD')).toBe('[encrypted message]');
  });

  it('does not treat ordinary text that merely starts like a marker as one', () => {
    expect(describeRichBody('e2ee:v2:not valid')).toBeNull();
    expect(describeRichBody('surprise:v1:')).toBeNull();
  });
});
