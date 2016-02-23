package gov.usgs.cida.gdp.wps.algorithm.heuristic.exception;

public class AlgorithmHeuristicException extends RuntimeException {

	private static final long serialVersionUID = 1467607385043208978L;

	public AlgorithmHeuristicException(String message) {
		super(message);
	}
	
	public AlgorithmHeuristicException(String message, Throwable cause) {
		super(message, cause);
	}
}
