'use client';

import ReactMarkdown from 'react-markdown';
import { remarkPlugins, rehypePlugins } from '@/lib/markdown';

export default function MarkdownRenderer({ content }: { content: string }) {
  return (
    <div className="prose prose-gray max-w-none
      prose-headings:font-bold
      prose-a:text-blue-600 prose-a:underline
      prose-code:bg-gray-100 prose-code:px-1 prose-code:rounded
      prose-pre:bg-gray-900 prose-pre:text-gray-100">
      <ReactMarkdown remarkPlugins={remarkPlugins} rehypePlugins={rehypePlugins}>{content}</ReactMarkdown>
    </div>
  );
}
