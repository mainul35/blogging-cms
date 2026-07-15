'use client';

import { useEffect, useState } from 'react';
import { getMailStatus } from './mailSettings';

// null = not yet known (still loading) -- callers should treat this the same
// as "not configured" for rendering purposes, to avoid a flash of
// mail-dependent UI that then disappears once the real status comes back.
export function useMailConfigured(): boolean | null {
  const [configured, setConfigured] = useState<boolean | null>(null);

  useEffect(() => {
    getMailStatus().then(status => setConfigured(status.configured));
  }, []);

  return configured;
}
