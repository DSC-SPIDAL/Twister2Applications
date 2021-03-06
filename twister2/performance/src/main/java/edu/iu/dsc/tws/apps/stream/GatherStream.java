package edu.iu.dsc.tws.apps.stream;

import edu.iu.dsc.tws.apps.data.DataGenerator;
import edu.iu.dsc.tws.apps.utils.JobParameters;
import edu.iu.dsc.tws.apps.utils.Utils;
import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.*;
import edu.iu.dsc.tws.comms.core.TWSCommunication;
import edu.iu.dsc.tws.comms.core.TWSNetwork;
import edu.iu.dsc.tws.comms.core.TaskPlan;
import edu.iu.dsc.tws.comms.mpi.io.gather.StreamingFinalGatherReceiver;
import edu.iu.dsc.tws.rsched.spi.container.IContainer;
import edu.iu.dsc.tws.rsched.spi.resource.ResourcePlan;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatherStream implements IContainer {
  private static final Logger LOG = Logger.getLogger(GatherStream.class.getName());
  private DataFlowOperation reduce;

  private ResourcePlan resourcePlan;

  private int id;

  private Config config;

  private JobParameters jobParameters;

  private long startSendingTime;

  private Map<Integer, ExternalSource> gatherWorkers = new HashMap<>();

  private List<Integer> tasksOfThisExec;

  private boolean executorWithDest = false;

  @Override
  public void init(Config cfg, int containerId, ResourcePlan plan) {
    LOG.log(Level.FINE, "Starting the example with container id: " + plan.getThisId());

    this.jobParameters = JobParameters.build(cfg);
    this.id = containerId;
    DataGenerator dataGenerator = new DataGenerator(jobParameters);

    // lets create the task plan
    TaskPlan taskPlan = Utils.createReduceTaskPlan(cfg, plan, jobParameters.getTaskStages());
    LOG.log(Level.FINE,"Task plan: " + taskPlan);
    //first get the communication config file
    TWSNetwork network = new TWSNetwork(cfg, taskPlan);

    TWSCommunication channel = network.getDataFlowTWSCommunication();

    Set<Integer> sources = new HashSet<>();
    Integer noOfSourceTasks = jobParameters.getTaskStages().get(0);
    for (int i = 0; i < noOfSourceTasks; i++) {
      sources.add(i);
    }
    int dest = jobParameters.getTaskStages().get(0);
    int destExecutor = taskPlan.getExecutorForChannel(dest);
    if (destExecutor == id) {
      executorWithDest = true;
    }
    Map<String, Object> newCfg = new HashMap<>();

    LOG.log(Level.FINE,"Setting up reduce dataflow operation");
    try {
      // this method calls the init method
      // I think this is wrong
      reduce = channel.gather(newCfg, Utils.getMessageTupe(jobParameters.getDataType()), 0, sources,
          dest, new StreamingFinalGatherReceiver(new FinalReduceReceiver()));

      Set<Integer> tasksOfExecutor = Utils.getTasksOfExecutor(id, taskPlan, jobParameters.getTaskStages(), 0);
      tasksOfThisExec = new ArrayList<>(tasksOfExecutor);
      ExternalSource source = null;
      int destExector = taskPlan.getExecutorForChannel(dest);
      boolean acked = destExector == id;
      for (int i : tasksOfExecutor) {
        source = new ExternalSource(i, Utils.getDataType(jobParameters.getDataType()), jobParameters, dataGenerator, id, acked, true);
        gatherWorkers.put(i, source);

        source.setOperation(reduce);

        StreamExecutor executor = new StreamExecutor(id, source, jobParameters);
        // the map thread where datacols is produced
        Thread mapThread = new Thread(executor);
        mapThread.start();
      }

      // we need to progress the communication
      while (true) {
        try {
          // progress the channel
          channel.progress();
          if (source != null) {
            startSendingTime = source.getStartSendingTime();
          }
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public class FinalReduceReceiver implements GatherBatchReceiver {
    Map<Integer, List<Long>> times = new HashMap<>();

    @Override
    public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
      LOG.log(Level.FINE, String.format("Initialize: " + expectedIds));
      for (Map.Entry<Integer, List<Integer>> e : expectedIds.entrySet()) {
        times.put(e.getKey(), new ArrayList<>());
      }
    }

    @Override
    public void receive(int target, Iterator<Object> iterator) {
      if (executorWithDest) {
        for (ExternalSource s : gatherWorkers.values()) {
          s.ack(0);
        }
      }
    }
  }
}
