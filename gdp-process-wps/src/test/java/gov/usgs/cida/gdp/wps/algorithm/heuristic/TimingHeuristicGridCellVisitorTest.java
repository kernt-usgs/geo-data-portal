package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 *
 * @author jiwalker
 */
public class TimingHeuristicGridCellVisitorTest {
	
	@Test
	public void testTEnd() {
		TimingHeuristicGridCellVisitor instance = new TimingHeuristicGridCellVisitor();
		instance.tStart(1);
		instance.tEnd(1);
		assertThat(instance.traverseContinue(), is(true));
	}

	@Test
	public void testTStart() {
		TimingHeuristicGridCellVisitor instance = new TimingHeuristicGridCellVisitor();
		boolean result = instance.tStart(1);
		assertThat(result, is(true));
	}

	@Test
	public void testEstimateTotalTime() throws InterruptedException {
		TimingHeuristicGridCellVisitor instance = new TimingHeuristicGridCellVisitor(2, 5, 1, Long.MAX_VALUE);
		instance.tStart(1);
		Thread.sleep(500);
		instance.tEnd(1);
		long result = instance.estimateTotalTime();
		assertThat(result, is(equalTo(5000l)));
	}
	
	@Test(expected = RuntimeException.class)
	public void testExceedsTotalTime() throws InterruptedException {
		TimingHeuristicGridCellVisitor instance = new TimingHeuristicGridCellVisitor(2, 5, 1, 4999);
		instance.tStart(1);
		Thread.sleep(500);
		instance.tEnd(1);
	}
}
