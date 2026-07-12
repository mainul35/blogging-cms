'use client';

import { useSession } from 'next-auth/react';
import { useState } from 'react';
import { api } from '@/lib/api';

export default function NewsletterOfferBanner() {
  const { data: session, status } = useSession();
  const [dismissed, setDismissed] = useState(false);
  const [subscribed, setSubscribed] = useState(false);

  if (status !== 'authenticated' || dismissed || subscribed || !session?.readerEmail) return null;

  const handleSubscribe = async () => {
    try {
      await api.post('/api/newsletter/subscribe', { email: session.readerEmail });
    } catch (err) {
      // Already-subscribed is a success outcome from the visitor's perspective,
      // not an error — this is a soft offer, not a critical path either way.
      if (!(err instanceof Error && err.message.toLowerCase().includes('already subscribed'))) {
        return;
      }
    }
    setSubscribed(true);
  };

  return (
    <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-6 flex items-center justify-between gap-4">
      <p className="text-sm text-blue-800">
        Subscribe <span className="font-medium">{session.readerEmail}</span> to the newsletter?
      </p>
      <div className="flex gap-2 shrink-0">
        <button
          onClick={handleSubscribe}
          className="text-sm px-3 py-1.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          Yes, subscribe
        </button>
        <button
          onClick={() => setDismissed(true)}
          className="text-sm px-3 py-1.5 text-blue-700 hover:underline"
        >
          Not now
        </button>
      </div>
    </div>
  );
}
