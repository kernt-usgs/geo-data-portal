package gov.usgs.cida.gdp.utilities.exception;


public class OPeNDAPUtilExceptionID {
    private final Long exceptionId;
    private String name;

    private OPeNDAPUtilExceptionID( String name, int id ) {
        this.name = name;
        this.exceptionId = Long.valueOf(id);
    }

    public String toString() { return this.name; }
    public Long value() { return this.exceptionId; }

    //-----------------------------------------
    // EXCEPTION DEFINITIONS
    //-----------------------------------------
    
    // BASIC UTILITY EXCEPTIONS
    public static final OPeNDAPUtilExceptionID GENERAL_EXCEPTION =
            new OPeNDAPUtilExceptionID("OPeNDAPUtilException : " +
                    "General internal exception thrown.", 0x00000);
    
    // NETCDF SPECIFIC EXCEPTIONS
    public static final OPeNDAPUtilExceptionID NETCDF_UNKNOWN_DIMENSION_EXCEPTION =
            new OPeNDAPUtilExceptionID("OPeNDAPUtilException : " +
                    "Unknown Dimension encountered in NetCDF Variable.", 0x01000);
}
