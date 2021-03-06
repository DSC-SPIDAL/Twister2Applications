package edu.iu.dsc.tws.apps.stockanalysis;

import edu.iu.dsc.tws.api.compute.TaskContext;
import edu.iu.dsc.tws.api.compute.executor.ExecutorContext;
import edu.iu.dsc.tws.api.compute.nodes.BaseSource;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.config.Context;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.apps.stockanalysis.utils.CleanMetric;
import edu.iu.dsc.tws.apps.stockanalysis.utils.Record;
import edu.iu.dsc.tws.apps.stockanalysis.utils.Utils;
import edu.iu.dsc.tws.apps.stockanalysis.utils.VectorPoint;
import edu.iu.dsc.tws.data.api.formatters.LocalCompleteTextInputPartitioner;
import edu.iu.dsc.tws.data.fs.io.InputSplit;
import edu.iu.dsc.tws.dataset.DataSource;
import edu.iu.dsc.tws.executor.core.ExecutionRuntime;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class DataProcessingSourceTask extends BaseSource {
    private static final Logger LOG = Logger.getLogger(DataPreprocessingSourceTask.class.getName());

    private static final long serialVersionUID = -5190777711234234L;

    private Map<Integer, VectorPoint> currentPoints = new HashMap<>();
    private Map<String, CleanMetric> metrics = new HashMap<>();
    private TreeMap<String, List<Date>> dates = new TreeMap<>();

    private int vectorCounter = 0;

    private String inputFile;
    private String outputFile;
    private String startDate;
    private BufferedReader bufRead;
    private FileReader input;
    private Record record;

    private DataSource<?, ?> source;
    private InputSplit<?> inputSplit;

    private EventTimeStockData eventTimeData;



    public DataProcessingSourceTask(String inputfile, String outputfile, String startdate) {
        this.inputFile = inputfile;
        this.outputFile = outputfile;
        this.startDate = startdate;
        this.eventTimeData = new EventTimeStockData(null, 0, 0L);
    }

    @Override
    public void execute() {
        try {
            Record record;
            if ((record = Utils.parseFile(bufRead, null, false)) != null) {
                writeToComputeTask(record);
            }
        } catch (IOException ioe) {
            throw new RuntimeException("IO Exception Occured" + ioe.getMessage());
        }
    }

    private void writeToComputeTask(Record record) {
        /**
         * TODO:
         *      1. Extract timestamp from the stock data record and convert it to a long value (this.eventTimeData.setEventTime(//);)
         *      2. Set the record to the EventTimeStockData object (eventTimeData.setRecord(record);
         *      3. Write to context
         * */
        //
        //


        context.write(Context.TWISTER2_DIRECT_EDGE, this.eventTimeData);
    }

    public void prepare(Config cfg, TaskContext context) {
        super.prepare(cfg, context);
        ExecutionRuntime runtime = (ExecutionRuntime) cfg.get(ExecutorContext.TWISTER2_RUNTIME_OBJECT);
        this.source = runtime.createInput(cfg, context, new LocalCompleteTextInputPartitioner(
                new Path(inputFile), context.getParallelism(), config));
        try {
            input = new FileReader(inputFile);
            bufRead = new BufferedReader(input);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("IOException Occured:" + e.getMessage());
        }
    }
}
