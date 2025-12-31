-- ============================================
-- SIMPLIFIED VERSION
-- ============================================
CREATE SEQUENCE session_error_log_seq;

CREATE TABLE session_error_log (
    error_id         NUMBER          DEFAULT session_error_log_seq.NEXTVAL PRIMARY KEY,
    emp_no           NUMBER,
    session_id       NUMBER,
    app_id           NUMBER,
    error_code       NUMBER,
    error_message    VARCHAR2(4000),
    error_time       TIMESTAMP       DEFAULT SYSTIMESTAMP,
    call_stack       CLOB,
    ip_address       VARCHAR2(45),
    resolved_flag    CHAR(1)         DEFAULT 'N' CHECK (resolved_flag IN ('Y', 'N')),
    resolved_by      VARCHAR2(30),
    resolved_date    DATE,
    created_date     DATE            DEFAULT SYSDATE,
    created_by       VARCHAR2(30)    DEFAULT USER
);

CREATE INDEX idx_err_time ON session_error_log(error_time);
CREATE INDEX idx_err_session ON session_error_log(session_id);
CREATE INDEX idx_err_emp ON session_error_log(emp_no);
