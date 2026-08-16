ALTER TABLE media ADD COLUMN media_format VARCHAR(24);

CREATE INDEX idx_media_format ON media (media_format);
