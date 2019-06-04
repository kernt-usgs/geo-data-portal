package gov.usgs.cida.gdp.utilities.exception;

public class GeoTiffUtilExceptionID {
    private final Long exceptionId;
    private String name;

    private GeoTiffUtilExceptionID( String name, int id ) {
        this.name = name;
        this.exceptionId = Long.valueOf(id);
    }

    public String toString() { return this.name; }
    public Long value() { return this.exceptionId; }

    //-----------------------------------------
    // EXCEPTION DEFINITIONS
    //-----------------------------------------
    
    // BASIC UTILITY EXCEPTIONS
    public static final GeoTiffUtilExceptionID GENERAL_EXCEPTION =
            new GeoTiffUtilExceptionID("GeoTiffUtilException : " +
                    "General internal exception thrown.", 0x00000);
    
    // I/O UTILITY EXCEPTIONS
    public static final GeoTiffUtilExceptionID ZIP_EXCEPTION = 
            new GeoTiffUtilExceptionID("GeoTiffUtilException : " +
                    "GeoTiff directory zip exception thrown.", 0x00100);
    
    // GEOTIFF SPECIFIC EXCEPTIONS
    public static final GeoTiffUtilExceptionID GEOTIFFWRITER_EXCEPTION =
            new GeoTiffUtilExceptionID("GeoTiffUtilException : " +
                    "NetCDF GeotiffWriter exception.", 0x01000);
}
