package edu.iu.dsc.tws.apps.stockanalysis;

import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.task.IMessage;
import edu.iu.dsc.tws.api.task.TaskContext;
import edu.iu.dsc.tws.api.task.nodes.BaseCompute;
import edu.iu.dsc.tws.apps.stockanalysis.utils.CleanMetric;
import edu.iu.dsc.tws.apps.stockanalysis.utils.Record;
import edu.iu.dsc.tws.apps.stockanalysis.utils.Utils;
import edu.iu.dsc.tws.apps.stockanalysis.utils.VectorPoint;

import java.util.*;
import java.util.logging.Logger;

public class DataPreprocessingComputeTask extends BaseCompute {

    private static final Logger LOG = Logger.getLogger(DataPreprocessingComputeTask.class.getName());

    private String vectorDirectory;
    private String distanceDirectory;
    private String edgeName;

    private int windowLength;
    private int slidingLength;
    private int index = 0;
    private int distanceType;

    private List<Record> recordList = new ArrayList<>();
    private List<Record> windowRecordList;

    private Map<String, CleanMetric> metrics = new HashMap();
    private Map<Integer, VectorPoint> currentPoints = new HashMap();

//    private List<String> vectorPoints;

//    public DataPreprocessingComputeTask(String vectordirectory, String distancedirectory,
//                                        int distancetype, String edgename) {
//        this.vectorDirectory = vectordirectory;
//        this.distanceDirectory = distancedirectory;
//        this.distanceType = distancetype;
//        this.edgeName = edgename;
//    }
//
//    @Override
//    public boolean execute(IMessage message) {
//        if (message.getContent() != null) {
//            LOG.fine("message content:" + message.getContent());
//            vectorPoints = new ArrayList<>();
//            if (message.getContent() != null) {
//                vectorPoints.add(String.valueOf(message.getContent()));
//            }
//        }
//
//        if (vectorPoints != null) {
//            context.write(edgeName, vectorPoints);
//        }
//        return true;
//    }

    public DataPreprocessingComputeTask(String vectordirectory, String distancedirectory, int distancetype,
                                        int windowlength, int slidinglength, String edgename) {
        this.vectorDirectory = vectordirectory;
        this.distanceDirectory = distancedirectory;
        this.distanceType = distancetype;
        this.edgeName = edgename;
        this.windowLength = windowlength;
        this.slidingLength = slidinglength;
    }

    @Override
    public boolean execute(IMessage message) {
        if (message.getContent() != null) {
            recordList.add((Record) message.getContent());
            index++;
            if (recordList.size() == windowLength) {
                windowRecordList = new ArrayList<>(windowLength);
                windowRecordList.addAll(recordList.subList(0, windowLength));
                remove(0, windowLength);
                processData(windowRecordList);
            }
        }
        return true;
    }

    private void remove(int startindex, int endindex) {
        for (int i = 0; i < endindex - startindex; i++) {
            recordList.remove(startindex);
        }
    }

    private void processData(List<Record> windowRecordList) {

        int noOfDays = 0;
        int splitCount = 0;
        int count = 0;
        int fullCount = 0;
        int capCount = 0;
        int index = 0;
        int size = -1;

        double totalCap = 0;

        String outFileName = "/home/kannan/out.csv";
        CleanMetric metric = this.metrics.get(outFileName);
        if (metric == null) {
            metric = new CleanMetric();
            this.metrics.put(outFileName, metric);
        }

        Date currentDate = windowRecordList.get(0).getDate();
        Date lastDate = Utils.addYear(currentDate);

        String start = Utils.getDateString(currentDate);
        String end = Utils.getDateString(lastDate);

        Date endDate = windowRecordList.get(windowLength - 1).getDate();

        LOG.info("window record list:" + windowRecordList.size()
                + "\tstartdate:" +  currentDate + "\tendate:" + endDate);
        for (int i = 1; i < windowRecordList.size(); i++) {
            if (!currentDate.equals(windowRecordList.get(i).getDate())) {
                noOfDays++;
            }
        }
        LOG.info("Number of unique days:" + noOfDays);

        //Vector generation
        for (int i = 0; i < windowRecordList.size(); i++) {
            Record record = windowRecordList.get(i);
            LOG.fine("Received record value is:" + record.getSymbol() + "\t" + record.getDateString()
                    +"\tand its date:" + record.getDate() + "\t" + "Number Of Days:" + noOfDays);
            int key = record.getSymbol();
            if (record.getFactorToAdjPrice() > 0) {
                splitCount++;
            }
            VectorPoint point = currentPoints.get(key);
        }
        writeData(windowRecordList);
    }

//    private Map<String, Map<Date, Integer>> findDates(String inFile) {
//
//        FileReader input = null;
//        Map<String, Map<Date, Integer>> outDates = new HashMap<>();
//        Map<String, Set<Date>> tempDates = new HashMap<>();
//
//        for (String dateRange : this.dates.keySet()) {
//            tempDates.put(dateRange, new TreeSet<Date>());
//        }
//
//        try {
//            input = new FileReader(inFile);
//            BufferedReader bufRead = new BufferedReader(input);
//            Record record;
//            while ((record = Utils.parseFile(bufRead, null, false)) != null) {
//                for (Map.Entry<String, List<Date>> ed : this.dates.entrySet()) {
//                    Date start = ed.getValue().get(0);
//                    Date end = ed.getValue().get(1);
//                    if (isDateWithing(start, end, record.getDate())) {
//                        Set<Date> tempDateList = tempDates.get(ed.getKey());
//                        tempDateList.add(record.getDate());
//                    }
//                }
//            }
//
//            for (Map.Entry<String, Set<Date>> ed : tempDates.entrySet()) {
//                Set<Date> datesSet = ed.getValue();
//                int i = 0;
//                Map<Date, Integer> dateIntegerMap = new HashMap<Date, Integer>();
//                for (Date d : datesSet) {
//                    dateIntegerMap.put(d, i);
//                    i++;
//                }
//                System.out.println("%%%% Key and date integer map size:" + ed.getKey() + "\t" + dateIntegerMap.size());
//                outDates.put(ed.getKey(), dateIntegerMap);
//            }
//        } catch (FileNotFoundException e) {
//            if (input != null) {
//                try {
//                    input.close();
//                } catch (IOException ignore) {
//                }
//            }
//        }
//
//        for (Map.Entry<String, Set<Date>> ed : tempDates.entrySet()) {
//            StringBuilder sb = new StringBuilder();
//            for (Date d : ed.getValue()) {
//                sb.append(Utils.formatter.format(d)).append(" ");
//            }
//            System.out.println(ed.getKey() + ":"  + sb.toString());
//        }
//        return outDates;
//    }
//
//
//    private boolean isDateWithing(Date start, Date end, Date compare) {
//        if (compare == null) {
//            System.out.println("Comapre null*****************");
//        }
//        return (compare.equals(start) || compare.after(start)) && compare.before(end);
//    }

    private void writeData(List<Record> windowRecordList) {
        context.write(edgeName, windowRecordList);
    }

    @Override
    public void prepare(Config cfg, TaskContext context) {
        super.prepare(cfg, context);
    }
}