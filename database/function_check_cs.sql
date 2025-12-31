CREATE OR REPLACE FUNCTION CHECK_CS (
    P_BIZ_ID      IN NUMBER,
    P_SESSION_ID  IN VARCHAR2,
    P_CS          IN VARCHAR2
) RETURN NUMBER
AS
    v_secret VARCHAR2(20) := 'SECRET123';
    v_hash   VARCHAR2(64);
BEGIN
    -- Generate hash
    v_hash := UPPER(
        RAWTOHEX(
            DBMS_CRYPTO.HASH(
                UTL_I18N.STRING_TO_RAW(P_SESSION_ID || v_secret || P_BIZ_ID, 'AL32UTF8'),
                DBMS_CRYPTO.HASH_SH256
            )
        )
    );
    
    RETURN CASE WHEN P_CS = v_hash THEN 1 ELSE 0 END;
END;
