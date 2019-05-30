//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.apps.mds;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.data.api.InputPartitioner;
import edu.iu.dsc.tws.data.api.formatters.BinaryInputPartitioner;
import edu.iu.dsc.tws.data.fs.Path;
import edu.iu.dsc.tws.data.fs.io.InputSplit;
import edu.iu.dsc.tws.dataset.DataSink;
import edu.iu.dsc.tws.dataset.DataSource;
import edu.iu.dsc.tws.executor.core.ExecutionRuntime;
import edu.iu.dsc.tws.executor.core.ExecutorContext;
import edu.iu.dsc.tws.task.api.BaseSource;
import edu.iu.dsc.tws.task.api.TaskContext;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.logging.Logger;

public class MDSDataObjectSource extends BaseSource {

  private static final Logger LOG = Logger.getLogger(MDSDataObjectSource.class.getName());

  private static final long serialVersionUID = -1L;

  /**
   * DataSource to partition the datapoints
   */
  private DataSource<?, ?> source;

  private DataSink<String> sink;

  private InputPartitioner inputPartitioner;

  /**
   * Edge name to write the partitoned datapoints
   */
  private String edgeName;
  private String dataDirectory;
  private int dataSize;

  public MDSDataObjectSource(String edgename, String dataDirectory, int size) {
    this.setEdgeName(edgename);
    this.setDataDirectory(dataDirectory);
    this.setDataSize(size);
  }

  private int getDataSize() {
    return dataSize;
  }

  private void setDataSize(int dataSize) {
    this.dataSize = dataSize;
  }

  private String getDataDirectory() {
    return dataDirectory;
  }

  private void setDataDirectory(String dataDirectory) {
    this.dataDirectory = dataDirectory;
  }

  /**
   * Getter property to set the edge name
   */
  private String getEdgeName() {
    return edgeName;
  }

  /**
   * Setter property to set the edge name
   */
  private void setEdgeName(String edgeName) {
    this.edgeName = edgeName;
  }

  /**
   * This method get the partitioned datapoints using the task index and write those values using
   * the respective edge name.
   */
  @Override
  public void execute() {
    Buffer buffer;
    byte[] line = new byte[2000];
    ByteBuffer byteBuffer = ByteBuffer.allocate(2000);
    byteBuffer.order(ByteOrder.BIG_ENDIAN);
    InputSplit inputSplit = source.getNextSplit(context.taskIndex());
    int count = 0;
    while (inputSplit != null) {
      try {
        while (!inputSplit.reachedEnd()) {
          while (inputSplit.nextRecord(line) != null) {
            byteBuffer.clear();
            byteBuffer.put(line);
            byteBuffer.flip();
            buffer = byteBuffer.asShortBuffer();
            short[] shortArray = new short[getDataSize()];
            ((ShortBuffer) buffer).get(shortArray);
            //For writing into the partition file
            //sink.add(context.taskIndex(), Arrays.toString(shortArray));
            context.write(getEdgeName(), shortArray);
            count++;
          }
        }
        LOG.info("count value is:" + count);
        inputSplit = null;
        //inputSplit = source.getNextSplit(context.taskIndex());
      } catch (Exception ioe) {
        throw new RuntimeException("IOException Occured:" + ioe.getMessage());
      }
    }
    //For writing into the partition file
    //sink.persist();
    context.end(getEdgeName());
  }

  @Override
  public void prepare(Config cfg, TaskContext context) {
    super.prepare(cfg, context);
    ExecutionRuntime runtime = (ExecutionRuntime) cfg.get(ExecutorContext.TWISTER2_RUNTIME_OBJECT);
    this.source = runtime.createInput(cfg, context, new BinaryInputPartitioner(
        new Path(getDataDirectory()), getDataSize() * Short.BYTES));

    //For writing into the partition file
    /*this.sink = new DataSink<>(cfg,
        new TextOutputWriter(FileSystem.WriteMode.OVERWRITE, new Path(getDataDirectory())));*/
  }
}
