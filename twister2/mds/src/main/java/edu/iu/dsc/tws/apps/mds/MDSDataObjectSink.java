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

import edu.iu.dsc.tws.api.compute.IMessage;
import edu.iu.dsc.tws.api.compute.TaskContext;
import edu.iu.dsc.tws.api.compute.modifiers.Collector;
import edu.iu.dsc.tws.api.compute.modifiers.IONames;
import edu.iu.dsc.tws.api.compute.nodes.BaseCompute;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.dataset.DataPartition;
import edu.iu.dsc.tws.dataset.partition.EntityPartition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class MDSDataObjectSink extends BaseCompute implements Collector {

    private static final Logger LOG = Logger.getLogger(MDSDataObjectSink.class.getName());

    private short[] dataPoints;
    private int numberOfColumns;

    private String inputKey;

    public MDSDataObjectSink() {
    }

    public MDSDataObjectSink(String inputkey, int length) {
        this.inputKey = inputkey;
        this.numberOfColumns = length;
    }

    public MDSDataObjectSink(int length) {
        this.numberOfColumns = length;
    }

    @Override
    public boolean execute(IMessage content) {
        List<short[]> values = new ArrayList<>();
        while (((Iterator) content.getContent()).hasNext()) {
            values.add((short[]) ((Iterator) content.getContent()).next());
        }
        LOG.info("Distance Matrix (Row X Column) Length:" + values.size() + "\tX\t" + values.get(0).length);
        dataPoints = new short[values.size() * numberOfColumns];
        int k = 0;
        for (short[] value : values) {
            for (short aValue : value) {
                dataPoints[k] = aValue;
                k = k + 1;
            }
        }
        return true;
    }

    @Override
    public void prepare(Config cfg, TaskContext context) {
        super.prepare(cfg, context);
    }

    @Override
    public DataPartition<short[]> get() {
        return new EntityPartition<>(dataPoints);
    }

    @Override
    public IONames getCollectibleNames() {
        return IONames.declare(inputKey);
    }
}

