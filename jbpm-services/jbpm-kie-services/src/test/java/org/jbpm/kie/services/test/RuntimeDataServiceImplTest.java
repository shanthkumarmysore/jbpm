/*
 * Copyright 2014 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.kie.services.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.kie.scanner.MavenRepository.getMavenRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.kie.test.util.AbstractBaseTest;
import org.jbpm.services.api.ProcessInstanceNotFoundException;
import org.jbpm.services.api.RuntimeDataService.EntryType;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.jbpm.services.api.model.UserTaskInstanceDesc;
import org.jbpm.services.api.model.VariableDesc;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.query.QueryContext;
import org.kie.internal.query.QueryFilter;
import org.kie.internal.task.api.AuditTask;
import org.kie.internal.task.api.model.TaskEvent;
import org.kie.scanner.MavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeDataServiceImplTest extends AbstractBaseTest {

private static final Logger logger = LoggerFactory.getLogger(KModuleDeploymentServiceTest.class);   
    
    private List<DeploymentUnit> units = new ArrayList<DeploymentUnit>();
    protected String correctUser = "testUser";
    protected String wrongUser = "wrongUser";
    
    private Long processInstanceId = null;
    private KModuleDeploymentUnit deploymentUnit = null;
       
    @Before
    public void prepare() {
    	configureServices();
    	logger.debug("Preparing kjar");
        KieServices ks = KieServices.Factory.get();
        ReleaseId releaseId = ks.newReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        List<String> processes = new ArrayList<String>();
        processes.add("repo/processes/general/EmptyHumanTask.bpmn");
        processes.add("repo/processes/general/humanTask.bpmn");
        processes.add("repo/processes/general/BPMN2-UserTask.bpmn2");
        processes.add("repo/processes/general/SimpleHTProcess.bpmn2");
        
        InternalKieModule kJar1 = createKieJar(ks, releaseId, processes);
        File pom = new File("target/kmodule", "pom.xml");
        pom.getParentFile().mkdir();
        try {
            FileOutputStream fs = new FileOutputStream(pom);
            fs.write(getPom(releaseId).getBytes());
            fs.close();
        } catch (Exception e) {
            
        }
        MavenRepository repository = getMavenRepository();
        repository.deployArtifact(releaseId, kJar1, pom);
        
        assertNotNull(deploymentService);
        
        deploymentUnit = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);
        
        deploymentService.deploy(deploymentUnit);
        units.add(deploymentUnit);
    	assertNotNull(processService);

    }
    
    @After
    public void cleanup() {
    	if (processInstanceId != null) {
    		try {
		    	// let's abort process instance to leave the system in clear state
		    	processService.abortProcessInstance(processInstanceId);
		    	
		    	ProcessInstance pi = processService.getProcessInstance(processInstanceId);    	
		    	assertNull(pi);
    		} catch (ProcessInstanceNotFoundException e) {
    			// ignore it as it was already completed/aborted
    		}
    	}
        cleanupSingletonSessionId();
        if (units != null && !units.isEmpty()) {
            for (DeploymentUnit unit : units) {
            	try {
                deploymentService.undeploy(unit);
            	} catch (Exception e) {
            		// do nothing in case of some failed tests to avoid next test to fail as well
            	}
            }
            units.clear();
        }
        close();
    }
    
    @Test
    public void testGetProcessByDeploymentId() {
    	Collection<ProcessDefinition> definitions = runtimeDataService.getProcessesByDeploymentId(deploymentUnit.getIdentifier(), new QueryContext());
    	assertNotNull(definitions);
    	
    	assertEquals(4, definitions.size());
    	List<String> expectedProcessIds = new ArrayList<String>();
    	expectedProcessIds.add("org.jbpm.writedocument.empty");
    	expectedProcessIds.add("org.jbpm.writedocument");
    	expectedProcessIds.add("UserTask");
    	expectedProcessIds.add("org.jboss.qa.bpms.HumanTask");
    	
    	for (ProcessDefinition def : definitions) {
    		assertTrue(expectedProcessIds.contains(def.getId()));
    	}
    }
    
    @Test
    public void testGetProcessByDeploymentIdAndProcessId() {
    	ProcessDefinition definition = runtimeDataService
    			.getProcessesByDeploymentIdProcessId(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	
    	assertNotNull(definition);
    	assertEquals("org.jbpm.writedocument", definition.getId());
    }
    
    @Test
    public void testGetProcessByFilter() {
    	Collection<ProcessDefinition> definitions = runtimeDataService.getProcessesByFilter("org.jbpm", new QueryContext());
    	
    	assertNotNull(definitions);
    	assertEquals(2, definitions.size());
    	List<String> expectedProcessIds = new ArrayList<String>();
    	expectedProcessIds.add("org.jbpm.writedocument.empty");
    	expectedProcessIds.add("org.jbpm.writedocument");
    	
    	for (ProcessDefinition def : definitions) {
    		assertTrue(expectedProcessIds.contains(def.getId()));
    	}
    }
    
    @Test
    public void testGetProcessByProcessId() {
    	ProcessDefinition definition = runtimeDataService.getProcessById("org.jbpm.writedocument");
    	
    	assertNotNull(definition);
    	assertEquals("org.jbpm.writedocument", definition.getId());
    }
    
    @Test
    public void testGetProcesses() {
    	Collection<ProcessDefinition> definitions = runtimeDataService.getProcesses(new QueryContext());
    	assertNotNull(definitions);
    	
    	assertEquals(4, definitions.size());
    	List<String> expectedProcessIds = new ArrayList<String>();
    	expectedProcessIds.add("org.jbpm.writedocument.empty");
    	expectedProcessIds.add("org.jbpm.writedocument");
    	expectedProcessIds.add("UserTask");
    	expectedProcessIds.add("org.jboss.qa.bpms.HumanTask");
    	
    	for (ProcessDefinition def : definitions) {
    		assertTrue(expectedProcessIds.contains(def.getId()));
    	}
    }
    
    @Test
    public void testGetProcessIds() {
    	Collection<String> definitions = runtimeDataService.getProcessIds(deploymentUnit.getIdentifier(), new QueryContext());
    	assertNotNull(definitions);
    	
    	assertEquals(4, definitions.size());
    	
    	assertTrue(definitions.contains("org.jbpm.writedocument.empty"));
    	assertTrue(definitions.contains("org.jbpm.writedocument"));  
    	assertTrue(definitions.contains("UserTask"));
    	assertTrue(definitions.contains("org.jboss.qa.bpms.HumanTask"));
    }
    
    @Test
    public void testGetProcessesSortByProcessName() {
    	Collection<ProcessDefinition> definitions = runtimeDataService.getProcesses(new QueryContext("ProcessName", true));
    	assertNotNull(definitions);
    	
    	assertEquals(4, definitions.size());
    	List<String> expectedProcessIds = new ArrayList<String>();
    	
    	expectedProcessIds.add("HumanTask");
    	expectedProcessIds.add("User Task");
    	expectedProcessIds.add("humanTaskSample");
    	expectedProcessIds.add("humanTaskSample");      	
    	
    	int index = 0;
    	for (ProcessDefinition def : definitions) {
    		assertEquals(def.getName(), expectedProcessIds.get(index));
    		
    		index++;
    	}
    }
    
    @Test
    public void testGetProcessesSortByProcessVersion() {
    	Collection<ProcessDefinition> definitions = runtimeDataService.getProcesses(new QueryContext("ProcessVersion", true));
    	assertNotNull(definitions);
    	
    	assertEquals(4, definitions.size());
    	List<String> expectedProcessIds = new ArrayList<String>();
    	expectedProcessIds.add("UserTask");
    	expectedProcessIds.add("org.jboss.qa.bpms.HumanTask");
    	expectedProcessIds.add("org.jbpm.writedocument.empty");
    	expectedProcessIds.add("org.jbpm.writedocument");    	
    	
    	int index = 0;
    	for (ProcessDefinition def : definitions) {
    		assertEquals(def.getId(), expectedProcessIds.get(index));
    		
    		index++;
    	}
    }
    
    @Test
    public void testGetProcessInstances() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(1, (int)instances.iterator().next().getState());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstances(new QueryContext("log.processName", false));
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(3, (int)instances.iterator().next().getState());
    }
    
    @Test
    public void testGetProcessInstancesByState() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for aborted only
    	states.add(3);
    	
    	instances = runtimeDataService.getProcessInstances(states, null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstances(states, null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(3, (int)instances.iterator().next().getState());
    }
    
    @Test
    public void testGetProcessInstancesByStateAndInitiator() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for active only
    	states.add(1);
    	
    	instances = runtimeDataService.getProcessInstances(states, correctUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(1, (int)instances.iterator().next().getState());
    	
    	instances = runtimeDataService.getProcessInstances(states, wrongUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size()); 
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstances(states, correctUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());    	
    }
    
    @Test
    public void testGetProcessInstancesByDeploymentIdAndState() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for aborted only
    	states.add(3);
    	
    	instances = runtimeDataService.getProcessInstancesByDeploymentId(deploymentUnit.getIdentifier(), states, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByDeploymentId(deploymentUnit.getIdentifier(), states, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(3, (int)instances.iterator().next().getState());
    }
    
    @Test
    public void testGetProcessInstancesByProcessId() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessDefinition("org.jbpm.writedocument", new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	
    	ProcessInstanceDesc instance = instances.iterator().next();
    	assertEquals(1, (int)instance.getState());
    	assertEquals("org.jbpm.writedocument", instance.getProcessId());
    	List<TaskSummary> taskSummaries = runtimeDataService.getTasksAssignedAsPotentialOwner("salaboy", new QueryFilter(0, 10));
    	assertNotNull(taskSummaries);
        assertEquals(1, taskSummaries.size());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByProcessDefinition("org.jbpm.writedocument", new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	instance = instances.iterator().next();
    	assertEquals(3, (int)instance.getState());
    	assertEquals("org.jbpm.writedocument", instance.getProcessId());
    }
    
    @Test
    public void testGetProcessInstancesByProcessIdAndStatus() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	Long processInstanceIdToAbort = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	
    	List<Integer> statuses = new ArrayList<Integer>();
    	statuses.add(ProcessInstance.STATE_ACTIVE);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessDefinition("org.jbpm.writedocument", statuses, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(2, instances.size());

    	for (ProcessInstanceDesc instance : instances) {
	    	assertEquals(ProcessInstance.STATE_ACTIVE, (int)instance.getState());
	    	assertEquals("org.jbpm.writedocument", instance.getProcessId());
    	}
    	
    	processService.abortProcessInstance(processInstanceIdToAbort);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessDefinition("org.jbpm.writedocument", statuses, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	ProcessInstanceDesc instance2 = instances.iterator().next();
    	assertEquals(ProcessInstance.STATE_ACTIVE, (int)instance2.getState());
    	assertEquals("org.jbpm.writedocument", instance2.getProcessId());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	statuses.clear();
    	statuses.add(ProcessInstance.STATE_ABORTED);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessDefinition("org.jbpm.writedocument", statuses, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(2, instances.size());

    	for (ProcessInstanceDesc instance : instances) {
	    	assertEquals(ProcessInstance.STATE_ABORTED, (int)instance.getState());
	    	assertEquals("org.jbpm.writedocument", instance.getProcessId());
    	}
    }
    
    @Test
    public void testGetProcessInstanceById() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	ProcessInstanceDesc instance = runtimeDataService.getProcessInstanceById(processInstanceId);
    	assertNotNull(instance);
    	assertEquals(1, (int)instance.getState());
    	assertEquals("org.jbpm.writedocument", instance.getProcessId());
    	
    	List<UserTaskInstanceDesc> tasks = instance.getActiveTasks();
    	assertNotNull(tasks);
    	assertEquals(1, tasks.size());
    	
    	UserTaskInstanceDesc activeTask = tasks.get(0);
    	assertNotNull(activeTask);
    	assertEquals(Status.Reserved.name(), activeTask.getStatus());
    	assertEquals(instance.getId(), activeTask.getProcessInstanceId());
    	assertEquals(instance.getProcessId(), activeTask.getProcessId());
    	assertEquals("Write a Document", activeTask.getName());
    	assertEquals("salaboy", activeTask.getActualOwner());
    	assertEquals(deploymentUnit.getIdentifier(), activeTask.getDeploymentId());
    	
    	processService.abortProcessInstance(processInstanceId);
    	    	
    	instance = runtimeDataService.getProcessInstanceById(processInstanceId);
    	processInstanceId = null;
    	assertNotNull(instance);
    	assertEquals(3, (int)instance.getState());
    	assertEquals("org.jbpm.writedocument", instance.getProcessId());
    	
    }
    
    @Test
    public void testGetProcessInstancesByProcessIdAndState() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for aborted only
    	states.add(3);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessId(states, "org.jbpm.writedocument", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByProcessId(states, "org.jbpm.writedocument", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(3, (int)instances.iterator().next().getState());
    }
    
    @Test
    public void testGetProcessInstancesByPartialProcessIdAndState() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for aborted only
    	states.add(3);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessId(states, "org.jbpm%", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByProcessId(states, "org.jbpm%", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(3, (int)instances.iterator().next().getState());
    }
    
    @Test
    public void testGetProcessInstancesByProcessIdAndStateAndInitiator() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for active only
    	states.add(1);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessId(states, "org.jbpm.writedocument", correctUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(1, (int)instances.iterator().next().getState());
    	
    	instances = runtimeDataService.getProcessInstancesByProcessId(states, "org.jbpm.writedocument", wrongUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size()); 
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByProcessId(states, "org.jbpm.writedocument", correctUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());    	
    }
    
    @Test
    public void testGetProcessInstancesByProcessNameAndState() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for aborted only
    	states.add(3);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessName(states, "humanTaskSample", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByProcessName(states, "humanTaskSample", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(3, (int)instances.iterator().next().getState());
    }
    
    @Test
    public void testGetProcessInstancesByPartialProcessNameAndState() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for aborted only
    	states.add(3);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessName(states, "human%", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByProcessName(states, "human%", null, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(3, (int)instances.iterator().next().getState());
    }
    
    @Test
    public void testGetProcessInstancesByProcessNameAndStateAndInitiator() {
    	Collection<ProcessInstanceDesc> instances = runtimeDataService.getProcessInstances(new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	List<Integer> states = new ArrayList<Integer>();
    	// search for active only
    	states.add(1);
    	
    	instances = runtimeDataService.getProcessInstancesByProcessName(states, "humanTaskSample", correctUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	assertEquals(1, (int)instances.iterator().next().getState());
    	
    	instances = runtimeDataService.getProcessInstancesByProcessName(states, "humanTaskSample", wrongUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size()); 
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    	
    	instances = runtimeDataService.getProcessInstancesByProcessName(states, "humanTaskSample", correctUser, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(0, instances.size());    	
    }
    
    @Test
    public void testGetProcessInstanceHistory() {
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	// get active nodes as history view
    	Collection<NodeInstanceDesc> instances = runtimeDataService.getProcessInstanceHistoryActive(processInstanceId, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	
    	// get completed nodes as history view
    	instances = runtimeDataService.getProcessInstanceHistoryCompleted(processInstanceId, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());
    	
    	// get both active and completed nodes as history view
    	instances = runtimeDataService.getProcessInstanceFullHistory(processInstanceId, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(3, instances.size());
    	
    	// get nodes filtered by type - start
    	instances = runtimeDataService.getProcessInstanceFullHistoryByType(processInstanceId, EntryType.START, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(2, instances.size());
    	
    	// get nodes filtered by type - end
    	instances = runtimeDataService.getProcessInstanceFullHistoryByType(processInstanceId, EntryType.END, new QueryContext());
    	assertNotNull(instances);
    	assertEquals(1, instances.size());  
    }
    
    @Test
    public void testGetNodeInstanceForWorkItem() {
    	
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	ProcessInstance instance = processService.getProcessInstance(processInstanceId);
    	assertNotNull(instance);
    	
    	Collection<NodeInstance> activeNodes = ((WorkflowProcessInstanceImpl) instance).getNodeInstances();
    	assertNotNull(activeNodes);
    	assertEquals(1, activeNodes.size());
    	
    	NodeInstance node = activeNodes.iterator().next();
    	assertNotNull(node);
    	assertTrue(node instanceof WorkItemNodeInstance);
    	
    	Long workItemId = ((WorkItemNodeInstance) node).getWorkItemId();
    	assertNotNull(workItemId);
    	
    	NodeInstanceDesc desc = runtimeDataService.getNodeInstanceForWorkItem(workItemId);
    	assertNotNull(desc);
    	assertEquals(processInstanceId, desc.getProcessInstanceId());
    	assertEquals("Write a Document", desc.getName());
    	assertEquals("HumanTaskNode", desc.getNodeType());
    }
    
    @Test
    public void testGetVariableLogs() {
    	Map<String, Object> params = new HashMap<String, Object>();
    	params.put("approval_document", "initial content");
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument", params);
    	assertNotNull(processInstanceId);
    	
    	Collection<VariableDesc> variableLogs = runtimeDataService.getVariableHistory(processInstanceId, "approval_document", new QueryContext());
    	assertNotNull(variableLogs);
    	assertEquals(1, variableLogs.size());
    	
    	processService.setProcessVariable(processInstanceId, "approval_document", "updated content");
    	
    	variableLogs = runtimeDataService.getVariableHistory(processInstanceId, "approval_document", new QueryContext());
    	assertNotNull(variableLogs);
    	assertEquals(2, variableLogs.size());
    	
    	processService.setProcessVariable(processInstanceId, "approval_reviewComment", "under review - content");
    	
    	variableLogs = runtimeDataService.getVariablesCurrentState(processInstanceId);
    	assertNotNull(variableLogs);
    	assertEquals(2, variableLogs.size());
    	
    	for (VariableDesc vDesc : variableLogs) {
    		if (vDesc.getVariableId().equals("approval_document")) {
    			assertEquals("updated content", vDesc.getNewValue());
    		} else if (vDesc.getVariableId().equals("approval_reviewComment")) {
    			assertEquals("under review - content", vDesc.getNewValue());
    		}
    	}
    }
    
    @Test
    public void testGetTaskByWorkItemId() {
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	ProcessInstance instance = processService.getProcessInstance(processInstanceId);
    	assertNotNull(instance);
    	
    	Collection<NodeInstance> activeNodes = ((WorkflowProcessInstanceImpl) instance).getNodeInstances();
    	assertNotNull(activeNodes);
    	assertEquals(1, activeNodes.size());
    	
    	NodeInstance node = activeNodes.iterator().next();
    	assertNotNull(node);
    	assertTrue(node instanceof WorkItemNodeInstance);
    	
    	Long workItemId = ((WorkItemNodeInstance) node).getWorkItemId();
    	assertNotNull(workItemId);
    	
    	UserTaskInstanceDesc userTask = runtimeDataService.getTaskByWorkItemId(workItemId);
    	assertNotNull(userTask);
    	assertEquals(processInstanceId, userTask.getProcessInstanceId());
    	assertEquals("Write a Document", userTask.getName());
    
    }
    
    @Test
    public void testGetTaskById() {
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	ProcessInstance instance = processService.getProcessInstance(processInstanceId);
    	assertNotNull(instance);
    	
    	List<Long> taskIds = runtimeDataService.getTasksByProcessInstanceId(processInstanceId);
    	assertNotNull(taskIds);
    	assertEquals(1, taskIds.size());
    	
    	Long taskId = taskIds.get(0);
    	
    	UserTaskInstanceDesc userTask = runtimeDataService.getTaskById(taskId);
    	assertNotNull(userTask);
    	assertEquals(processInstanceId, userTask.getProcessInstanceId());
    	assertEquals("Write a Document", userTask.getName());
    
    }
    
    @Test
    public void testGetTaskOwned() {
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jboss.qa.bpms.HumanTask");
    	assertNotNull(processInstanceId);
    	
    	ProcessInstance instance = processService.getProcessInstance(processInstanceId);
    	assertNotNull(instance);
    	
    	List<TaskSummary> tasks = runtimeDataService.getTasksOwned("john", new QueryFilter(0, 5));
    	assertNotNull(tasks);
    	assertEquals(1, tasks.size());

    	TaskSummary userTask = tasks.get(0);
    	
    	assertNotNull(userTask);
    	assertEquals(processInstanceId, userTask.getProcessInstanceId());
    	assertEquals("Hello", userTask.getName());
    	assertEquals("john", userTask.getActualOwnerId());
    	assertEquals("Reserved", userTask.getStatusId());
    	assertNotNull(userTask.getActualOwner());
    }
    
    @Test
    public void testGetTaskAssignedAsBusinessAdmin() {
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	List<TaskSummary> tasks = runtimeDataService.getTasksAssignedAsBusinessAdministrator("Administrator", new QueryFilter(0, 5));
    	assertNotNull(tasks);
    	assertEquals(1, tasks.size());
    	
    	TaskSummary userTask = tasks.get(0);    
    	assertNotNull(userTask);
    	assertEquals(processInstanceId, userTask.getProcessInstanceId());
    	assertEquals("Write a Document", userTask.getName());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    }
    
    @Test
    public void testGetTaskAssignedAsBusinessAdminPaging() {

    	for (int i = 0; i < 10; i++) {
    	
    		processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	}
    	
    	List<TaskSummary> tasks = runtimeDataService.getTasksAssignedAsBusinessAdministrator("Administrator", new QueryFilter(0, 5));
    	assertNotNull(tasks);
    	assertEquals(5, tasks.size());
    	
    	TaskSummary userTask = tasks.get(0);    
    	assertNotNull(userTask);    	
    	assertEquals("Write a Document", userTask.getName());
    
    	Collection<ProcessInstanceDesc> activeProcesses = runtimeDataService.getProcessInstances(new QueryContext(0,  20));
    	for (ProcessInstanceDesc pi : activeProcesses) {
    		processService.abortProcessInstance(pi.getId());
    	}
    }
    
    @Test
    public void testGetTaskAssignedAsBusinessAdminPagingAndFiltering() {
    	long processInstanceId = -1;
    	for (int i = 0; i < 10; i++) {
    	
    		processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	}
    	
    	Map<String, Object> params = new HashMap<String, Object>();
        params.put("processInstanceId", processInstanceId);
		QueryFilter qf = new QueryFilter( "t.taskData.processInstanceId = :processInstanceId", 
                            params, "t.id", false);
		qf.setOffset(0);
		qf.setCount(5);
    	
    	List<TaskSummary> tasks = runtimeDataService.getTasksAssignedAsBusinessAdministrator("Administrator", qf);
    	assertNotNull(tasks);
    	assertEquals(1, tasks.size());
    	
    	TaskSummary userTask = tasks.get(0);    
    	assertNotNull(userTask);    	
    	assertEquals("Write a Document", userTask.getName());
    	assertEquals(processInstanceId, (long)userTask.getProcessInstanceId());
    
    	Collection<ProcessInstanceDesc> activeProcesses = runtimeDataService.getProcessInstances(new QueryContext(0,  20));
    	for (ProcessInstanceDesc pi : activeProcesses) {
    		processService.abortProcessInstance(pi.getId());
    	}
    }
    
    @Test
    public void testGetTasksAssignedAsPotentialOwnerByStatusPagingAndFiltering() {
    	List<Long> processInstanceIds = new ArrayList<Long>();
    	for (int i = 0; i < 10; i++) {
    	
    		processInstanceIds.add(processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument"));
    	}
    	
    	Map<String, Object> params = new HashMap<String, Object>();
        params.put("processInstanceId", processInstanceIds);
		QueryFilter qf = new QueryFilter( "t.taskData.processInstanceId in (:processInstanceId)", 
                            params, "t.id", false);
		qf.setOffset(0);
		qf.setCount(5);
		
		List<Status> statuses = new ArrayList<Status>();
		statuses.add(Status.Ready);
		statuses.add(Status.Reserved);
    	
    	List<TaskSummary> tasks = runtimeDataService.getTasksAssignedAsPotentialOwnerByStatus("salaboy", statuses, qf);
    	assertNotNull(tasks);
    	assertEquals(5, tasks.size());
    	
    	TaskSummary userTask = tasks.get(0);    
    	assertNotNull(userTask);    	
    	assertEquals("Write a Document", userTask.getName());
    
    	Collection<ProcessInstanceDesc> activeProcesses = runtimeDataService.getProcessInstances(new QueryContext(0,  20));
    	for (ProcessInstanceDesc pi : activeProcesses) {
    		processService.abortProcessInstance(pi.getId());
    	}
    }
    
    @Test
    public void testTasksByStatusByProcessInstanceIdPagingAndFiltering() {
    	
    	Long pid = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	
    	List<Status> statuses = new ArrayList<Status>();
		statuses.add(Status.Ready);
		statuses.add(Status.Reserved);
    	
    	List<TaskSummary> tasks = runtimeDataService.getTasksAssignedAsPotentialOwnerByStatus("salaboy", statuses, new QueryFilter(0, 5));
    	assertNotNull(tasks);
    	assertEquals(1, tasks.size());
    	
    	long taskId = tasks.get(0).getId();
    	
    	userTaskService.start(taskId, "salaboy");
    	userTaskService.complete(taskId, "salaboy", null);
    	
    	Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", "Review Document");
		QueryFilter qf = new QueryFilter( "t.name = :name", 
                            params, "t.id", false);
		qf.setOffset(0);
		qf.setCount(5);

    	
    	tasks = runtimeDataService.getTasksByStatusByProcessInstanceId(pid, statuses, qf);
    	assertNotNull(tasks);
    	assertEquals(1, tasks.size());
    	
    	TaskSummary userTask = tasks.get(0);    
    	assertNotNull(userTask);    	
    	assertEquals("Review Document", userTask.getName());
    	
    	tasks = runtimeDataService.getTasksByStatusByProcessInstanceId(pid, statuses, new QueryFilter(0, 5));
    	assertNotNull(tasks);
    	assertEquals(2, tasks.size());
    
    	Collection<ProcessInstanceDesc> activeProcesses = runtimeDataService.getProcessInstances(new QueryContext(0,  20));
    	for (ProcessInstanceDesc pi : activeProcesses) {
    		processService.abortProcessInstance(pi.getId());
    	}
    }
    
    @Test
    public void testGetTaskAudit() {
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	ProcessInstance instance = processService.getProcessInstance(processInstanceId);
    	assertNotNull(instance);
    	
    	Collection<NodeInstance> activeNodes = ((WorkflowProcessInstanceImpl) instance).getNodeInstances();
    	assertNotNull(activeNodes);
    	assertEquals(1, activeNodes.size());
    	
    	NodeInstance node = activeNodes.iterator().next();
    	assertNotNull(node);
    	assertTrue(node instanceof WorkItemNodeInstance);
    	
    	Long workItemId = ((WorkItemNodeInstance) node).getWorkItemId();
    	assertNotNull(workItemId);
    	
    	List<AuditTask> auditTasks = runtimeDataService.getAllAuditTask("salaboy", new QueryFilter(0, 10));
    	assertNotNull(auditTasks);
    	assertEquals(1, auditTasks.size());
    	assertEquals("Write a Document", auditTasks.get(0).getName());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    
    }
    
    @Test
    public void testGetTaskEvents() {
    	processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "org.jbpm.writedocument");
    	assertNotNull(processInstanceId);
    	
    	ProcessInstance instance = processService.getProcessInstance(processInstanceId);
    	assertNotNull(instance);
    	
    	Collection<NodeInstance> activeNodes = ((WorkflowProcessInstanceImpl) instance).getNodeInstances();
    	assertNotNull(activeNodes);
    	assertEquals(1, activeNodes.size());
    	
    	NodeInstance node = activeNodes.iterator().next();
    	assertNotNull(node);
    	assertTrue(node instanceof WorkItemNodeInstance);
    	
    	Long workItemId = ((WorkItemNodeInstance) node).getWorkItemId();
    	assertNotNull(workItemId);
    	
    	UserTaskInstanceDesc userTask = runtimeDataService.getTaskByWorkItemId(workItemId);
    	assertNotNull(userTask);
    	
    	List<TaskEvent> auditTasks = runtimeDataService.getTaskEvents(userTask.getTaskId(), new QueryFilter());
    	assertNotNull(auditTasks);
    	assertEquals(1, auditTasks.size());
    	assertEquals(TaskEvent.TaskEventType.ADDED, auditTasks.get(0).getType());
    	
    	userTaskService.start(userTask.getTaskId(), "salaboy");
    	
    	auditTasks = runtimeDataService.getTaskEvents(userTask.getTaskId(), new QueryFilter());
    	assertNotNull(auditTasks);
    	assertEquals(2, auditTasks.size());
    	assertEquals(TaskEvent.TaskEventType.ADDED, auditTasks.get(0).getType());
    	assertEquals(TaskEvent.TaskEventType.STARTED, auditTasks.get(1).getType());
    	
    	processService.abortProcessInstance(processInstanceId);
    	processInstanceId = null;
    
    }
}
