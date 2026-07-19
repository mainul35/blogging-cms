import { api } from './api';
import type { MediumImportRequest, MediumImportResponse } from '@/types/mediumImport';

export async function importMediumArticle(payload: MediumImportRequest): Promise<MediumImportResponse> {
  return api.post<MediumImportResponse>('/api/admin/medium-import', payload);
}
