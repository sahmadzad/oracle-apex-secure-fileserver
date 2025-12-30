create or replace function validate_session(
    p_app_id    IN NUMBER,
    p_session_id IN NUMBER) return NUMBER
  AS
v_is_valid Number;
BEGIN
    SELECT COUNT(*)
    INTO v_is_valid
    FROM APEX_WORKSPACE_SESSIONS s
    WHERE s.APEX_SESSION_ID = p_session_id
      AND s.SESSION_LIFE_TIMEOUT_ON > SYSTIMESTAMP
      AND s.SESSION_IDLE_TIMEOUT_ON > SYSTIMESTAMP;
   Return v_is_valid ;
END;
