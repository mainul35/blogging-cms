import { api } from './api';
import type {
  MediumImportJobResponse,
  MediumImportJobStatusResponse,
  MediumImportRequest,
} from '@/types/mediumImport';

export async function startMediumImport(payload: MediumImportRequest): Promise<MediumImportJobResponse> {
  return api.post<MediumImportJobResponse>('/api/admin/medium-import', payload);
}

export async function getMediumImportJobStatus(jobId: string): Promise<MediumImportJobStatusResponse> {
  return api.get<MediumImportJobStatusResponse>(`/api/admin/medium-import/${jobId}/status`);
}

// Kept out of the page component so the polling loop itself (interval,
// give-up point) lives in one place. Resolves once the job reaches DONE or
// FAILED; rejects if polling runs past MAX_POLL_MS without reaching either --
// matches the backend's own JOB_SAFETY_TIMEOUT (5min) with headroom for the
// poll interval itself, so this gives up only after the backend genuinely
// could have too.
const POLL_INTERVAL_MS = 2500;
const MAX_POLL_MS = 6 * 60 * 1000;

export async function pollMediumImportJob(
  jobId: string,
  onUpdate?: (status: MediumImportJobStatusResponse) => void,
): Promise<MediumImportJobStatusResponse> {
  const deadline = Date.now() + MAX_POLL_MS;
  while (Date.now() < deadline) {
    const status = await getMediumImportJobStatus(jobId);
    onUpdate?.(status);
    if (status.state === 'DONE' || status.state === 'FAILED') {
      return status;
    }
    await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
  }
  throw new Error(
    'Import is still running after several minutes -- it may still finish in the background. ' +
      'Check the Posts list shortly, or docker logs blog_backend for progress.',
  );
}
