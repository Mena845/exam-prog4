CREATE TABLE image_submission (
                                  id             UUID PRIMARY KEY,
                                  original_filename VARCHAR(255) NOT NULL,
                                  email          VARCHAR(255) NOT NULL,
                                  created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
                                  original_s3_key VARCHAR(512) NOT NULL,
                                  bw_s3_key      VARCHAR(512),
                                  status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_image_submission_created_at ON image_submission (created_at DESC);