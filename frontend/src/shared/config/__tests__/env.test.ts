import { describe, it, expect } from 'vitest';
import { config } from '@/shared/config/env';

describe('config.isDevelopment', () => {
  it('is true in test environment', () => {
    expect(config.isDevelopment).toBe(true);
  });

  it('import.meta.env.DEV is true', () => {
    expect(import.meta.env.DEV).toBe(true);
  });
});
