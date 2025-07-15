-- FIAP X Database Initialization Script
-- This script creates the necessary tables for the microservices architecture

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'USER',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL
);

-- Create file_uploads table
CREATE TABLE IF NOT EXISTS file_uploads (
    id VARCHAR(255) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    hash VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) DEFAULT 'uploaded',
    user_id VARCHAR(255) REFERENCES users(id),
    username VARCHAR(100),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    output_file VARCHAR(255) NULL,
    frame_count INTEGER NULL,
    error_message TEXT NULL
);

-- Create processing_jobs table
CREATE TABLE IF NOT EXISTS processing_jobs (
    id VARCHAR(255) PRIMARY KEY,
    file_id VARCHAR(255) REFERENCES file_uploads(id),
    status VARCHAR(50) DEFAULT 'pending',
    progress INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    output_file VARCHAR(255) NULL,
    frame_count INTEGER NULL,
    error_message TEXT NULL
);

-- Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    data JSONB NULL,
    user_id VARCHAR(255) REFERENCES users(id),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_file_uploads_user_id ON file_uploads(user_id);
CREATE INDEX IF NOT EXISTS idx_file_uploads_status ON file_uploads(status);
CREATE INDEX IF NOT EXISTS idx_file_uploads_uploaded_at ON file_uploads(uploaded_at);
CREATE INDEX IF NOT EXISTS idx_processing_jobs_file_id ON processing_jobs(file_id);
CREATE INDEX IF NOT EXISTS idx_processing_jobs_status ON processing_jobs(status);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications(is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at);

-- Insert default admin user (password: admin123)
INSERT INTO users (id, username, email, password_hash, role) 
VALUES (
    'admin_001',
    'admin',
    'admin@fiapx.com',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', -- admin123
    'ADMIN'
) ON CONFLICT (username) DO NOTHING;

-- Create a view for file uploads with user information
CREATE OR REPLACE VIEW file_uploads_with_user AS
SELECT 
    fu.*,
    u.username as owner_username,
    u.email as owner_email
FROM file_uploads fu
LEFT JOIN users u ON fu.user_id = u.id;

-- Create a view for processing jobs with file information
CREATE OR REPLACE VIEW processing_jobs_with_file AS
SELECT 
    pj.*,
    fu.filename,
    fu.original_filename,
    fu.user_id,
    fu.username
FROM processing_jobs pj
LEFT JOIN file_uploads fu ON pj.file_id = fu.id; 