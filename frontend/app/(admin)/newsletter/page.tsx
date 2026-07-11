import NewsletterSubscriberTable from '@/components/admin/NewsletterSubscriberTable';
import SendDigestForm from '@/components/admin/SendDigestForm';

export const metadata = { title: 'Newsletter Management' };

export default function AdminNewsletterPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Newsletter Management</h1>
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* Subscriber table — takes 2/3 width on xl screens */}
        <div className="xl:col-span-2">
          <NewsletterSubscriberTable />
        </div>
        {/* Send digest — sidebar card */}
        <div>
          <SendDigestForm />
        </div>
      </div>
    </div>
  );
}
