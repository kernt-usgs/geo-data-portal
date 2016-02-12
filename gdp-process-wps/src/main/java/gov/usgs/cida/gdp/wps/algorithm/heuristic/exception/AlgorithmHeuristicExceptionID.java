package gov.usgs.cida.gdp.wps.algorithm.heuristic.exception;

public class AlgorithmHeuristicExceptionID {
    private final Long exceptionId;
    private String name;

    private AlgorithmHeuristicExceptionID( String name, int id ) {
        this.name = name;
        this.exceptionId = Long.valueOf(id);
    }

    public String toString() { return this.name; }
    public Long value() { return this.exceptionId; }

    //-----------------------------------------
    // EXCEPTION DEFINITIONS
    //-----------------------------------------
    
    // BASIC EXCEPTIONS
    public static final AlgorithmHeuristicExceptionID GENERAL_EXCEPTION =
            new AlgorithmHeuristicExceptionID("AlgorithmHeuristicException : " +
                    "General internal exception thrown.", 0x00000);
    
    public static final AlgorithmHeuristicExceptionID UNINITIALIZED_EXCEPTION =
            new AlgorithmHeuristicExceptionID("AlgorithmHeuristicException : " +
                    "Heuristic has not been initialized.", 0x00001);

    // GDP Exceptions
    public static final AlgorithmHeuristicExceptionID GDP_GRID_UTILITY_EXCEPTION =
            new AlgorithmHeuristicExceptionID("AlgorithmHeuristicException GDP Grid Utility Exception : " +
                    "Internal Grid Utility exception was thrown.", 0x01000);

    public static final AlgorithmHeuristicExceptionID GDP_MAX_TIME_EXCEEDED_EXCEPTION =
            new AlgorithmHeuristicExceptionID("AlgorithmHeuristicException Max Time Exception : " +
                    "Estimated process time exceeds preset limit.", 0x01001);
}
