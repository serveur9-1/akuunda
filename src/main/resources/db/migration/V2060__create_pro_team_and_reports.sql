-- Pro Team Members: membres de l'équipe d'un marchand (Espace Pro)
CREATE TABLE pro_team_members (
    id UUID PRIMARY KEY,
    owner_email VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(50) NOT NULL DEFAULT 'OPERATOR',
    department VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP,
    invited_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    CONSTRAINT uq_pro_team_owner_email UNIQUE (owner_email, email)
);

-- Pro Reports: rapports générés pour un marchand
CREATE TABLE pro_reports (
    id UUID PRIMARY KEY,
    owner_email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    period_from DATE,
    period_to DATE,
    row_count INTEGER DEFAULT 0,
    total_amount DOUBLE PRECISION DEFAULT 0,
    currency VARCHAR(10),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
