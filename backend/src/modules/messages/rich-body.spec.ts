import { describeRichBody } from './rich-body';

describe('describeRichBody', () => {
  it('recognises catalog and custom stickers', () => {
    expect(describeRichBody('sticker:v1:love_01')).toBe('[sticker]');
    expect(describeRichBody('sticker:v1:c:2:%F0%9F%92%97:%D8%AF%D9%88%D8%B3%D8%AA%D8%AA')).toBe('[sticker]');
  });

  it('recognises gifs only with a valid id and dimensions', () => {
    expect(describeRichBody('gif:v1:xT9IgzoKnwFNmISR8I:200:150')).toBe('[GIF]');
    expect(describeRichBody('gif:v1:bad id:200:150')).toBeNull();
    expect(describeRichBody('gif:v1:xT9IgzoKnwFNmISR8I:200')).toBeNull();
  });

  it('recognises poll and location markers', () => {
    expect(describeRichBody('poll:v1:3f2b8c1e-5d4a-4c1b-9e77-0a1b2c3d4e5f')).toBe('[poll]');
    expect(describeRichBody('poll:v1:nope')).toBeNull();
    expect(describeRichBody('loc:v1:35.689200:51.389000')).toBe('[location]');
    expect(describeRichBody('loc:v1:-33.8:151.2')).toBe('[location]');
    expect(describeRichBody('loc:v1:abc:def')).toBeNull();
  });

  it('recognises contact cards', () => {
    expect(describeRichBody('contact:v1:Sara%20K:%2B989123456789')).toBe('[contact]');
    expect(describeRichBody('contact:v1:Sara:09123456789')).toBe('[contact]');
    expect(describeRichBody('contact:v1:only-name')).toBeNull();
  });

  it('leaves ordinary text alone', () => {
    expect(describeRichBody('sticker of my love')).toBeNull();
    expect(describeRichBody('hello gif:v1:abc')).toBeNull();
    expect(describeRichBody('')).toBeNull();
  });
});
