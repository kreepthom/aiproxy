-- Add request_body column to request_logs table
ALTER TABLE request_logs ADD COLUMN request_body TEXT COMMENT '请求体内容' AFTER request_path;