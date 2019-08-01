package edu.iu.dsc.tws.apps.stockanalysis;

import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.config.Context;
import edu.iu.dsc.tws.api.task.IMessage;
import edu.iu.dsc.tws.api.task.TaskContext;
import edu.iu.dsc.tws.api.task.nodes.BaseCompute;
import edu.iu.dsc.tws.apps.stockanalysis.utils.CleanMetric;
import edu.iu.dsc.tws.apps.stockanalysis.utils.Record;
import edu.iu.dsc.tws.apps.stockanalysis.utils.Utils;
import edu.iu.dsc.tws.apps.stockanalysis.utils.VectorPoint;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class DataPreprocessingComputeTask extends BaseCompute {

    private static final Logger LOG = Logger.getLogger(DataPreprocessingComputeTask.class.getName());

    private String vectorDirectory;
    private String distanceDirectory;
    private String edgeName;

    private Date startDate;
    private Date endDate;

    private int windowLength;
    private int slidingLength;
    private int distanceType;
    private int totalCount = 0;
    private int counter = 0;
    private int index = 0;
    int vectorCounter;

    private int getTotalList = 0;

    private Map<String, CleanMetric> metrics = new HashMap();
    private List<Record> recordList = new ArrayList<>();
    private Map<Integer, VectorPoint> currentPoints = new HashMap();

    public DataPreprocessingComputeTask(String vectordirectory, String distancedirectory,
                                        int distancetype, int windowlength, int slidinglength,
                                        String startDate, String edgename) {
        this.vectorDirectory = vectordirectory;
        this.distanceDirectory = distancedirectory;
        this.distanceType = distancetype;
        this.edgeName = edgename;
        this.windowLength = windowlength;
        this.slidingLength = slidinglength;
        this.startDate = Utils.parseDateString(startDate);
        this.endDate = addYear(this.startDate);
    }

    @Override
    public boolean execute(IMessage message) {
        Record tempRecord;
        if (message.getContent() != null) {
            Record record = (Record) message.getContent();
            //if (record.getDate().compareTo(startDate) > 0 && record.getDate().before(endDate)) {
            if (record.getDate().equals(startDate) || record.getDate().after(startDate)
                    && record.getDate().before(endDate)) {
                recordList.add((Record) message.getContent());
                counter++;
            } else if (record.getDate().after(endDate)) {
                LOG.info("counter value:" + counter);
                tempRecord = ((Record) message.getContent());
                LOG.info("%%%%%%% temp record details:%%%%%%%" + tempRecord.getDate());
                counter = 0;
                LOG.info("Before Processing start date:" + startDate + "\t" + "enddate:" + endDate);
                LOG.info("%%%%% Before Processing Record List Size:%%%%%%" + recordList.size());
                startDate = addDate(startDate, slidingLength);
                endDate = addDate(endDate, slidingLength);
                processRecord(recordList);
                LOG.info("After Processing start date:" + startDate + "\t" + "enddate:" + endDate);
                LOG.info("%%%%% After Processing Record List Size:%%%%%%" + recordList.size());
                //recordList.add(tempRecord);
                // send the list to matrix computation
            }
        }
        return true;
    }

    private boolean processRecord(List<Record> recordList) {
        boolean flag = process(recordList);
        if (flag) {
            removeSlidingList();
        }
        return true;
    }

    private void removeSlidingList() {
        int count = 0;
        while (true) {
            if (recordList.get(0).getDate().compareTo(startDate) < 0) {
                recordList.remove(0);
                count++;
            } else {
                break;
            }
        }
        LOG.info("count of records removed:" + count);
    }

    private boolean process(List<Record> recordList) {
        Map<Date, Integer> dateIntegerMap = new LinkedHashMap<>();
        int index = 0;
        for (int i = 0; i < recordList.size(); i++) {
            if (!dateIntegerMap.containsKey(recordList.get(i).getDate())) {
                dateIntegerMap.put(recordList.get(i).getDate(), index);
            }
        }
        LOG.info("Date IntegerMap Size:" + dateIntegerMap.entrySet().size());
        processData(recordList, dateIntegerMap);
        return true;
    }

    private void processData(List<Record> recordList, Map<Date, Integer> dateIntegerMap) {
        BufferedWriter bufWriter = null;
        BufferedReader bufRead = null;

        LOG.info("Processing " + getTotalList + "\twindow data segements");
        this.getTotalList++;
        int noOfDays = dateIntegerMap.size();
        LOG.info("number of days:" + noOfDays);
        //int noOfDays = recordList.size();
        int size = -1;
        int splitCount = 0;
        int count = 0;
        int fullCount = 0;
        int capCount = 0;

        vectorCounter = 0;

        String outFileName = vectorDirectory + "/" + Utils.dateToString(startDate)
                + Utils.dateToString(endDate) + ".csv";
        LOG.info("output file name:" + outFileName);
        CleanMetric metric = this.metrics.get(outFileName);
        if (metric == null) {
            metric = new CleanMetric();
            this.metrics.put(outFileName, metric);
        }

        double totalCap = 0;
        try {
            FileOutputStream fos = new FileOutputStream(new File(outFileName));
            bufWriter = new BufferedWriter(new OutputStreamWriter(fos));

            //Vector generation
            for (Record record : recordList) {
                count++;
                int key = record.getSymbol();
                if (record.getFactorToAdjPrice() > 0) {
                    splitCount++;
                }
                VectorPoint point = currentPoints.get(key);
                if (point == null) {
                    point = new VectorPoint(key, noOfDays, true);
                    currentPoints.put(key, point);
                }

                /*LOG.info("Received record value is:" + record.getSymbol()
                    + "\trecord date string:" + record.getDateString()
                    + "\tand its start date:" + startDate
                    + "\tand its end date:" + endDate
                    + "\tNumber Of Days:" + noOfDays
                    + "\tvector:" + point);*/

                // figure out the index
                int index = dateIntegerMap.get(record.getDate());
                if (!point.add(record.getPrice(), record.getFactorToAdjPrice(), record.getFactorToAdjVolume(), metric, index)) {
                    metric.dupRecords++;
                    LOG.info("dup: " + record.serialize());
                }
                point.addCap(record.getVolume() * record.getPrice());

                if (point.noOfElements() == size) {
                    fullCount++;
                }

                if (currentPoints.size() > 2000 && size == -1) {
                    List<Integer> pointSizes = new ArrayList<Integer>();
                    for (VectorPoint v : currentPoints.values()) {
                        pointSizes.add(v.noOfElements());
                    }
                    size = mostCommon(pointSizes);
                    LOG.info("Number of stocks per period: " + size);
                }

                // now write the current vectors, also make sure we have the size determined correctly
                //if (currentPoints.size() > 1000 && size != -1 && fullCount > 750) {
                LOG.fine("Processed: " + count);
                totalCap += writeVectors(bufWriter, noOfDays, metric);
                capCount++;
                fullCount = 0;
                //}
            }

            //totalCap += writeVectors(size, metric);
            //capCount++;

            LOG.info("Vector Counter Value:" + vectorCounter);
            LOG.info("Split count: " + " = " + splitCount);
            LOG.info("Total stocks: " + currentPoints.size());
            metric.stocksWithIncorrectDays = currentPoints.size();
            //currentPoints.clear();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open the file", e);
        } finally {
            try {
                if (bufWriter != null) {
                    bufWriter.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    Map<Integer, String> vectorsMap = new LinkedHashMap<>();

    private double writeVectors(BufferedWriter bufWriter, int size, CleanMetric metric) throws IOException {
        double capSum = 0;
        int count = 0;
        for (Iterator<Map.Entry<Integer, VectorPoint>> it = currentPoints.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, VectorPoint> entry = it.next();
            VectorPoint v = entry.getValue();
            if (v.noOfElements() == size) {
                metric.totalStocks++;

                if (!v.cleanVector(metric)) {
                    // System.out.println("Vector not valid: " + outFileName + ", " + v.serialize());
                    metric.invalidStocks++;
                    it.remove();
                    continue;
                }
                String sv = v.serialize();

                // if many points are missing, this can return null
                if (sv != null) {
                    capSum += v.getTotalCap();
                    count++;
                    LOG.info("serialized value:" + sv);
                    bufWriter.write(sv);
                    bufWriter.newLine();
                    vectorsMap.put(getTotalList, sv);
                    // remove it from map
                    vectorCounter++;
                    metric.writtenStocks++;
                } else {
                    metric.invalidStocks++;
                }
                it.remove();
            } else {
                metric.lenghtWrong++;
            }
        }
        LOG.fine("vectors map size:" + vectorsMap.size());
        LOG.fine("%%% Vector Counter value:%%%" + vectorCounter);
        context.write(Context.TWISTER2_DIRECT_EDGE, vectorsMap);
        return capSum;
    }

    private void remove(int startindex, int endindex) {
        LOG.info("start index:" + startindex + "\t" + endindex);
        for (int i = 0; i < endindex - startindex; i++) {
            recordList.remove(startindex);
        }
    }

    private List<Date> getSlidingList(Map<Date, Integer> dateIntegerMap) {
        List<Date> slidingList = new LinkedList<>();
        for (Map.Entry<Date, Integer> dateIntegerEntry : dateIntegerMap.entrySet()) {
            Date start = dateIntegerEntry.getKey();
            slidingList.add(start);
            if (slidingList.size() == slidingLength) {
                break;
            }
        }
        return slidingList;
    }

    private boolean isDateWithing(Date start, Date end, Date compare) {
        if (compare == null) {
            System.out.println("Comapre null*****************");
        }
        return (compare.equals(start) || compare.after(start)) && compare.before(end);
    }

    private static Date addYear(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.YEAR, 1);
        return cal.getTime();
    }

    private static Date addDate(Date date, int slidingLength) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, slidingLength);
        return cal.getTime();
    }

    public static <T> T mostCommon(List<T> list) {
        Map<T, Integer> map = new HashMap<T, Integer>();
        for (T t : list) {
            Integer val = map.get(t);
            map.put(t, val == null ? 1 : val + 1);
        }
        Map.Entry<T, Integer> max = null;
        for (Map.Entry<T, Integer> e : map.entrySet()) {
            if (max == null || e.getValue() > max.getValue())
                max = e;
        }
        LOG.info("max key is:" + max.getKey());
        return max.getKey();
    }

    @Override
    public void prepare(Config cfg, TaskContext context) {
        super.prepare(cfg, context);
    }
}