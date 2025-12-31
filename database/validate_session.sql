create or replace FUNCTION validate_session(
    p_app_id     IN VARCHAR2,
    p_empno      IN VARCHAR2,
    p_app_cs     IN VARCHAR2,
    p_session_id IN VARCHAR2
) RETURN NUMBER
IS
    v_is_valid         NUMBER;
BEGIN
    -- ============================================
    -- LOG SESSION VALIDATION ATTEMPT
    -- ============================================
    DBMS_APPLICATION_INFO.SET_MODULE(
        module_name => 'VALIDATE_SESSION',
        action_name => 'CHECKING'
    );
    
    SELECT COUNT(*)
    INTO v_is_valid
    FROM APEX_WORKSPACE_SESSIONS s
    WHERE s.apex_session_id = p_session_id
      AND s.session_life_timeout_on > SYSTIMESTAMP
      AND s.session_idle_timeout_on > SYSTIMESTAMP
      -- Optional: Uncomment for workspace-specific validation
      -- AND s.workspace_id = p_app_id
      AND ROWNUM = 1; -- Optimize performance
    IF p_app_cs IS NULL THEN
      Return   v_is_valid;
    ELSE
        -- Validate CS token if provided

        RETURN  check_cs(p_empno, p_session_id, p_app_cs);
    END IF;    
    DBMS_APPLICATION_INFO.SET_MODULE(
        module_name => 'VALIDATE_SESSION',
        action_name => CASE 
                        WHEN v_is_valid = 1
                        THEN 'VALID' 
                        ELSE 'INVALID' 
                       END
    );

END validate_session;
