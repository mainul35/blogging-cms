import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';

export const remarkPlugins = [remarkGfm];

// rehype-raw lets embedded raw HTML render (needed for resizable <img> tags and
// the alignment <div> wrapper) — paired with a restricted sanitize schema so
// post content can't smuggle in <script>, event handlers, or other raw HTML.
const sanitizeSchema = {
  ...defaultSchema,
  tagNames: [...(defaultSchema.tagNames ?? []), 'div'],
  attributes: {
    ...defaultSchema.attributes,
    img: [...(defaultSchema.attributes?.img ?? []), 'width', 'height', 'id'],
    // Restricted to these exact values — not an open 'style' attribute — so this
    // can't be used to smuggle in arbitrary CSS.
    div: [...(defaultSchema.attributes?.div ?? []), ['align', 'left', 'center', 'right', 'justify']],
  },
};

export const rehypePlugins = [rehypeRaw, [rehypeSanitize, sanitizeSchema]];
