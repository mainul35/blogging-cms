export interface MediumImportRequest {
  fetchUrl: string;
  ownershipConfirmed: boolean;
}

export interface MediumImportJobResponse {
  jobId: string;
}

export type MediumImportJobState = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export interface MediumImportJobStatusResponse {
  jobId: string;
  state: MediumImportJobState;
  // Populated only once state === 'DONE'
  postId?: number;
  slug?: string;
  title?: string;
  imagesImported?: number;
  imagesFailed?: number;
  warnings?: string[];
  // Populated only once state === 'FAILED'
  errorMessage?: string;
}
