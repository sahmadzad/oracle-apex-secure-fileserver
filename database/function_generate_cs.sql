CREATE OR REPLACE FUNCTION GENERATE_CS (
    P_BIZ_ID      IN VARCHAR2,
    P_SESSION_ID  IN VARCHAR2
) RETURN VARCHAR2
AS
    v_secret VARCHAR2(20) := 'SECRET123'; -- Your secret key
BEGIN
    RETURN UPPER(
        RAWTOHEX(
            DBMS_CRYPTO.HASH(
                UTL_I18N.STRING_TO_RAW(P_SESSION_ID || v_secret || P_BIZ_ID, 'AL32UTF8'),
                DBMS_CRYPTO.HASH_SH256
            )
        )
    );
END GENERATE_CS;

