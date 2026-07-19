export interface MediumImportRequest {
  fetchUrl: string;
  ownershipConfirmed: boolean;
}

export interface MediumImportResponse {
  postId: number;
  slug: string;
  title: string;
  imagesImported: number;
  imagesFailed: number;
  warnings: string[];
}
