package gov.usgs.cida.gdp.coreprocessing.analysis.grid;

import gov.usgs.cida.gdp.coreprocessing.analysis.Statistic;
import gov.usgs.cida.gdp.coreprocessing.analysis.statistics.IStatistics1D;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Statistics1DWriter {
    
    
    public final static String DEFAULT_BLOCK_SEPARATOR;
    
    public final static String ALL_ATTRIBUTES_LABEL = "ALL ATTRIBUTES";
    public final static String ALL_TIMESTEPS_LABEL = "ALL TIMESTEPS";
    public final static String TIMESTEPS_LABEL = "TIMESTEP";

    static {
        String blockSeparator;
        try {
            blockSeparator = System.lineSeparator();
        } catch (Exception e) {
            blockSeparator = "\n";
        }
        DEFAULT_BLOCK_SEPARATOR = blockSeparator;
    } 
    
    public enum GroupBy {
        STATISTIC,
        FEATURE_ATTRIBUTE;
    }

    private final List<Object> attributeList;
    private final String variableName;
    private final String variableUnits;
    private final List<Statistic> statisticList;
    private final boolean groupByStatistic;
    private final String tokenSeparator;
    private final String blockSeparator;
    private final boolean summarizeTimeStep;
    private final boolean summarizeFeatureAttribute;
    private final Writer writer;

    private final StringBuilder lineSB;

    public Statistics1DWriter(
            List<Object> attributeList,
            String variableName,
            String variableUnits,
            List<? extends Statistic> statisticList,
            boolean groupByStatistic,
            String tokenSeparator,
            String blockSeparator,
            Writer writer) {
        this(attributeList,variableName,variableUnits,statisticList,groupByStatistic,tokenSeparator,blockSeparator,false,false,writer);
    }
    
    public Statistics1DWriter(
            List<Object> attributeList,
            String variableName,
            String variableUnits,
            List<? extends Statistic> statisticList,
            boolean groupByStatistic,
            String tokenSeparator,
            String blockSeparator,
            boolean summarizeTimeStep,
            boolean summarizeFeatureAttribute,
            Writer writer) {

        this.attributeList = Collections.unmodifiableList(attributeList);
        this.variableName = variableName;
        this.variableUnits = variableUnits;
        this.statisticList = Collections.unmodifiableList(statisticList);
        this.groupByStatistic = groupByStatistic;
        this.tokenSeparator = tokenSeparator;
        this.blockSeparator = blockSeparator == null ? DEFAULT_BLOCK_SEPARATOR : blockSeparator;
        this.summarizeTimeStep = summarizeTimeStep;
        this.summarizeFeatureAttribute = summarizeFeatureAttribute;
        this.writer = writer;

        lineSB = new StringBuilder();

    }
    
    public boolean isSummarizeTimeStep() {
        return summarizeTimeStep;
    }
    
    public boolean isSummarizeFeatureAttribute() {
        return summarizeFeatureAttribute;
    }

    public void writeHeader(Collection<String> rowLabels) throws IOException {

        int sCount = statisticList.size();
        String[] statisticLabel = new String[sCount];
        if (variableUnits != null && variableUnits.length() > 0) {
            for(int sIndex = 0; sIndex < sCount; ++sIndex) {
                Statistic field = statisticList.get(sIndex);
                statisticLabel[sIndex] = field.getNeedsUnits() ?
                    field.name() + "(" + String.format(field.getUnitFormat(), variableUnits) + ")" :
                    field.name();
            }
        } else {
            for(int sIndex = 0; sIndex < sCount; ++sIndex) {
                statisticLabel[sIndex] = statisticList.get(sIndex).name();
            }
        }

        int aCount = attributeList.size();
        String attributeLabel[] = new String[aCount];
        for (int aIndex = 0; aIndex < aCount; ++aIndex) {
            attributeLabel[aIndex] = attributeList.get(aIndex).toString();
        }

        lineSB.setLength(0);
        if (rowLabels != null && rowLabels.size() > 0) {
            // pad with delimieters to align headers.
            int rowLabelCount = rowLabels.size();
            for (int rowLabelIndex = 1; rowLabelIndex < rowLabelCount; ++rowLabelIndex ) {
                lineSB.append(tokenSeparator);
            }
        }
        if (groupByStatistic) {
            for (int sIndex = 0; sIndex < sCount; ++sIndex) {
                for (int aIndex = 0; aIndex < aCount; ++aIndex ) {
                    lineSB.append(tokenSeparator).append(attributeLabel[aIndex]);
                }
                if (summarizeTimeStep) { 
                    lineSB.append(tokenSeparator).append(ALL_ATTRIBUTES_LABEL);
                }
            }
        } else {
            for (int aIndex = 0; aIndex < aCount; ++aIndex )
                for (int sIndex = 0; sIndex < sCount; ++sIndex) {{
                    lineSB.append(tokenSeparator).append(attributeLabel[aIndex]);
                }
            }
            if (summarizeTimeStep) {
                for (int sIndex = 0; sIndex < statisticLabel.length; ++sIndex) {
                    lineSB.append(tokenSeparator).append(ALL_ATTRIBUTES_LABEL);
                }
            }
        }
        writer.write(lineSB.toString());
        writer.write(blockSeparator);

        lineSB.setLength(0);
        if (rowLabels != null && rowLabels.size() > 0) {
            Iterator<String> rowLabelIterator = rowLabels.iterator();
            lineSB.append(rowLabelIterator.next());
            while (rowLabelIterator.hasNext()) {
                lineSB.append(tokenSeparator).append(rowLabelIterator.next());
            }
        }
        
        if (groupByStatistic) {
            for (int sIndex = 0; sIndex < sCount; ++sIndex) {
                int aCountX = summarizeTimeStep ? aCount + 1 : aCount; 
                for (int aIndex = 0; aIndex < aCountX; ++ aIndex) {
                    lineSB.append(tokenSeparator).append(statisticLabel[sIndex]);
                }
            }
        } else {
            int aCountX = summarizeTimeStep ? aCount + 1 : aCount;
            for (int aIndex = 0; aIndex < aCountX; ++ aIndex) {
                for (int sIndex = 0; sIndex < sCount; ++sIndex) {
                    lineSB.append(tokenSeparator).append(statisticLabel[sIndex]);
                }
            }
        }
        writer.write(lineSB.toString());
        writer.write(blockSeparator);
    }

    public void writeRow(
            Collection<String> rowLabels,
            Collection<? extends IStatistics1D> rowValues,
            IStatistics1D rowSummary)
            throws IOException
    {
        lineSB.setLength(0);
        if (rowLabels != null && rowLabels.size() > 0) {
            Iterator<String> rowLabelIterator = rowLabels.iterator();
            lineSB.append(rowLabelIterator.next());
            while (rowLabelIterator.hasNext()) {
                lineSB.append(tokenSeparator).append(rowLabelIterator.next());
            }
        }

        if (groupByStatistic) {
            for (Statistic field : statisticList) {
                for (IStatistics1D rowValue : rowValues) {
                    lineSB.append(tokenSeparator).append(field.getValue(rowValue));
                }
                if (summarizeTimeStep && rowSummary != null) {
                    // value for ALL features across timestep
                    lineSB.append(tokenSeparator).append(field.getValue(rowSummary));
                }
            }
        } else {
            for (IStatistics1D rowValue : rowValues) {
                for (Statistic field : statisticList) {
                    lineSB.append(tokenSeparator).append(field.getValue(rowValue));
                }
            }
            if (summarizeTimeStep && rowSummary != null) {
                // value for ALL features across timestep
                for (Statistic field : statisticList) {
                    lineSB.append(tokenSeparator).append(field.getValue(rowSummary));
                }
            }
        }
        
        writer.write(lineSB.toString());
        writer.write(blockSeparator);
    }
    
    public static List<String> buildRowLabel(String tLabel, String zLabel) {
        List<String> rowLabelList = new ArrayList<String>(2);
        rowLabelList.add(tLabel == null ? "" : tLabel);
        if (zLabel != null) {
            rowLabelList.add(zLabel);
        }
        return rowLabelList;
    }
}
