package edu.iu.dsc.tws.apps.stockanalysis.utils;

import edu.iu.dsc.tws.api.comms.LogicalPlan;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.WorkerEnvironment;
import edu.iu.dsc.tws.proto.jobmaster.JobMasterAPI;
import edu.iu.dsc.tws.proto.utils.WorkerResourceUtils;
import org.apache.commons.cli.Option;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EUtils {
    private static final Logger LOG = Logger.getLogger(EUtils.class.getName());

    private EUtils() {
    }

    /**
     * Let assume we have 2 tasks per container and one additional for first container,
     * which will be the destination
     *
     * @return task plan
     */
    public static LogicalPlan createReduceTaskPlan(Config cfg,
                                                   int workerID,
                                                   List<JobMasterAPI.WorkerInfo> workerInfoList,
                                                   int noOfTasks) {
        int numberOfWorkers = workerInfoList.size();
        LOG.log(Level.INFO, "No of workers: " + numberOfWorkers);
        Map<Integer, Set<Integer>> executorToGraphNodes = new HashMap<>();
        Map<Integer, Set<Integer>> groupsToExeuctors = new HashMap<>();
        int thisExecutor = workerID;

        Map<String, List<JobMasterAPI.WorkerInfo>> containersPerNode = new HashMap<>();
        for (JobMasterAPI.WorkerInfo workerInfo : workerInfoList) {
            String name = Integer.toString(workerInfo.getWorkerID());
            List<JobMasterAPI.WorkerInfo> containerList;
            if (!containersPerNode.containsKey(name)) {
                containerList = new ArrayList<>();
                containersPerNode.put(name, containerList);
            } else {
                containerList = containersPerNode.get(name);
            }
            containerList.add(workerInfo);
        }

        int taskPerExecutor = noOfTasks / numberOfWorkers;
        for (int i = 0; i < numberOfWorkers; i++) {
            Set<Integer> nodesOfExecutor = new HashSet<>();
            for (int j = 0; j < taskPerExecutor; j++) {
                nodesOfExecutor.add(i * taskPerExecutor + j);
            }
            if (i == 0) {
                nodesOfExecutor.add(noOfTasks);
            }
            executorToGraphNodes.put(i, nodesOfExecutor);
        }

        int i = 0;
        // we take each container as an executor
        for (Map.Entry<String, List<JobMasterAPI.WorkerInfo>> e : containersPerNode.entrySet()) {
            Set<Integer> executorsOfGroup = new HashSet<>();
            for (JobMasterAPI.WorkerInfo workerInfo : e.getValue()) {
                executorsOfGroup.add(workerInfo.getWorkerID());
            }
            groupsToExeuctors.put(i, executorsOfGroup);
            i++;
        }

        String print = printMap(executorToGraphNodes);
        LOG.fine("Executor To Graph: " + print);
        print = printMap(groupsToExeuctors);
        LOG.fine("Groups to executors: " + print);
        // now lets create the task plan of this, we assume we have map tasks in all the processes
        // and reduce task in 0th process
        return new LogicalPlan(executorToGraphNodes, groupsToExeuctors,
                Collections.emptyMap(), thisExecutor);
    }

    private static int nextExecutorId(int current, int noOfContainers) {
        if (current < noOfContainers - 1) {
            return current + 1;
        } else {
            return 0;
        }
    }

    public static String printMap(Map<Integer, Set<Integer>> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Set<Integer>> e : map.entrySet()) {
            sb.append(e.getKey() + " : ");
            for (Integer i : e.getValue()) {
                sb.append(i).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public static Option createOption(String opt, boolean hasArg,
                                      String description, boolean required) {
        Option symbolListOption = new Option(opt, hasArg, description);
        symbolListOption.setRequired(required);
        return symbolListOption;
    }

    /**
     * Create task plan according to stages
     *
     * @param workerEnv         worker environment
     * @param noOfTaskEachStage no of tasks at each stage
     * @return task plan
     */

    public static LogicalPlan createStageLogicalPlan(WorkerEnvironment workerEnv,
                                                     List<Integer> noOfTaskEachStage) {
        int workerID = workerEnv.getWorkerId();
        List<JobMasterAPI.WorkerInfo> workerList = workerEnv.getWorkerList();

        int noOfContainers = workerList.size();
        Map<Integer, Set<Integer>> executorToGraphNodes = new HashMap<>();
        Map<Integer, Set<Integer>> groupsToExeuctors = new HashMap<>();

        Map<String, List<JobMasterAPI.WorkerInfo>> containersPerNode =
                WorkerResourceUtils.getWorkersPerNode(workerList);

        int totalTasksPreviously = 0;
        for (int noOfTasks : noOfTaskEachStage) {
            int currentExecutorId = 0;
            for (int i = 0; i < noOfTasks; i++) {
                Set<Integer> nodesOfExecutor;
                if (executorToGraphNodes.get(currentExecutorId) == null) {
                    nodesOfExecutor = new HashSet<>();
                } else {
                    nodesOfExecutor = executorToGraphNodes.get(currentExecutorId);
                }
                nodesOfExecutor.add(totalTasksPreviously + i);
                executorToGraphNodes.put(currentExecutorId, nodesOfExecutor);
                // we go to the next executor
                currentExecutorId = nextExecutorId(currentExecutorId, noOfContainers);
            }
            totalTasksPreviously += noOfTasks;
        }

        Map<String, Set<Integer>> nodeToTasks = new HashMap<>();

        int i = 0;
        for (Map.Entry<String, List<JobMasterAPI.WorkerInfo>> entry : containersPerNode.entrySet()) {
            Set<Integer> executorsOfGroup = new HashSet<>();
            for (JobMasterAPI.WorkerInfo workerInfo : entry.getValue()) {
                executorsOfGroup.add(workerInfo.getWorkerID());
                Set<Integer> tasksInNode = nodeToTasks.computeIfAbsent(
                        workerInfo.getNodeInfo().getNodeIP(),
                        k -> new HashSet<>());
                tasksInNode.addAll(executorToGraphNodes.get(workerInfo.getWorkerID()));
            }
            groupsToExeuctors.put(i, executorsOfGroup);
            i++;
        }
//    groupsToExeuctors.put(0, new HashSet<>(Arrays.asList(1)));
//    groupsToExeuctors.put(1, new HashSet<>(Arrays.asList(2)));
//    groupsToExeuctors.put(2, new HashSet<>(Arrays.asList(0)));
//    groupsToExeuctors.put(3, new HashSet<>(Arrays.asList(3)));

        return new LogicalPlan(executorToGraphNodes, groupsToExeuctors, nodeToTasks, workerID);
    }

    public static Set<Integer> getTasksOfExecutor(int exec, LogicalPlan plan,
                                                  List<Integer> noOfTaskEachStage, int stage) {
        Set<Integer> out = new HashSet<>();
        int noOfTasks = noOfTaskEachStage.get(stage);
        int total = 0;
        for (int i = 0; i < stage; i++) {
            total += noOfTaskEachStage.get(i);
        }

        Set<Integer> tasksOfExec = plan.getChannelsOfExecutor(exec);
        for (int i = 0; i < noOfTasks; i++) {
            if (tasksOfExec != null && tasksOfExec.contains(i + total)) {
                out.add(i + total);
            }
        }
        return out;
    }
}
