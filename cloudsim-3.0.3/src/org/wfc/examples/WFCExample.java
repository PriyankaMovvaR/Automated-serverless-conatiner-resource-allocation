/**
 * Copyright 2019-2020 ArmanRiazi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.wfc.examples;

import java.io.File;
import java.lang.management.*;
import java.text.DecimalFormat;
import java.util.*;
import org.cloudbus.cloudsim.container.core.*;
import org.cloudbus.cloudsim.container.hostSelectionPolicies.*;
import org.cloudbus.cloudsim.container.resourceAllocatorMigrationEnabled.*;
import org.cloudbus.cloudsim.container.schedulers.*;
import org.cloudbus.cloudsim.container.utils.IDs;
import org.cloudbus.cloudsim.container.vmSelectionPolicies.*;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.HarddriveStorage;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.container.core.*;
import org.cloudbus.cloudsim.container.resourceAllocators.*;
import org.cloudbus.cloudsim.container.containerProvisioners.*;
import org.cloudbus.cloudsim.container.containerVmProvisioners.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;
import org.workflowsim.CondorVM;
import org.workflowsim.Task;
import org.workflowsim.WorkflowDatacenter;
import org.workflowsim.Job;
import org.workflowsim.WFCEngine;
import org.workflowsim.WFCPlanner;
import org.workflowsim.utils.*;
import org.cloudbus.cloudsim.util.Conversion;
import org.workflowsim.failure.*;
import org.workflowsim.utils.DistributionGenerator;
import org.wfc.core.WFCDatacenter;
import org.wfc.core.WFCConstants;
import org.workflowsim.utils.Parameters.ClassType;
/**
 * @author Priyanka Movva R
 * @author Arpitha Hiresadrahalli Dayananda
 *
 */
public class WFCExample {

	private static String experimentName = "Threshold based Serverless Containers Resource allocation";
	private static int num_user = 1;
	private static boolean trace_flag = false; // mean trace events
	private static boolean failure_flag = false;
	private static List<Container> containerList;
	private static List<ContainerHost> hostList;
	public static List<? extends ContainerVm> vmList;

	public static void main(String[] args) {
		try {

			WFCConstants.CAN_PRINT_SEQ_LOG = false;
			WFCConstants.CAN_PRINT_SEQ_LOG_Just_Step = false;
			WFCConstants.ENABLE_OUTPUT = false;
			WFCConstants.FAILURE_FLAG = false;
			WFCConstants.RUN_AS_STATIC_RESOURCE = true;

			FailureParameters.FTCMonitor ftc_monitor = null;
			FailureParameters.FTCFailure ftc_failure = null;
			FailureParameters.FTCluteringAlgorithm ftc_method = null;
			DistributionGenerator[][] failureGenerators = null;

			Log.printLine("Starting " + experimentName + " ... ");

			String daxPath = "./config/dax/Montage_" + (WFCConstants.WFC_NUMBER_CLOUDLETS - 1) + ".xml";

			File daxFile = new File(daxPath);
			if (!daxFile.exists()) {
				Log.printLine("Warning: Please replace daxPath with the physical path in your working environment!");
				return;
			}

			if (failure_flag) {
				/**
				 * MONITOR_JOB classifies failures based on the level of jobs; MONITOR_VM
				 * classifies failures based on the vm id; MOINTOR_ALL does not do any
				 * classification; MONITOR_NONE does not record any failiure.
				 */
				ftc_monitor = FailureParameters.FTCMonitor.MONITOR_ALL;
				/**
				 * Similar to FTCMonitor, FTCFailure controls the way how we generate failures.
				 */
				ftc_failure = FailureParameters.FTCFailure.FAILURE_ALL;
				/**
				 * In this example, we have no clustering and thus it is no need to do Fault
				 * Tolerant Clustering. By default, WorkflowSim will just rety all the failed
				 * task.
				 */
				ftc_method = FailureParameters.FTCluteringAlgorithm.FTCLUSTERING_NOOP;
				/**
				 * Task failure rate for each level
				 *
				 */
				failureGenerators = new DistributionGenerator[1][1];
				failureGenerators[0][0] = new DistributionGenerator(DistributionGenerator.DistributionFamily.WEIBULL,
						100, 1.0, 30, 300, 0.78);
			}

			Parameters.SchedulingAlgorithm sch_method = Parameters.SchedulingAlgorithm.MINMIN;// local
			Parameters.PlanningAlgorithm pln_method = Parameters.PlanningAlgorithm.INVALID;// global-stage
			WFCReplicaCatalog.FileSystem file_system = WFCReplicaCatalog.FileSystem.LOCAL;

			OverheadParameters op = new OverheadParameters(0, null, null, null, null, 0);

			ClusteringParameters.ClusteringMethod method = ClusteringParameters.ClusteringMethod.NONE;
			ClusteringParameters cp = new ClusteringParameters(0, 0, method, null);

			if (failure_flag) {
				FailureParameters.init(ftc_method, ftc_monitor, ftc_failure, failureGenerators);
			}

			Parameters.init(WFCConstants.WFC_NUMBER_VMS, daxPath, null, null, op, cp, sch_method, pln_method, null, 0);
			WFCReplicaCatalog.init(file_system);

			if (failure_flag) {
				FailureMonitor.init();
				FailureGenerator.init();
			}

			WFCReplicaCatalog.init(file_system);

			Calendar calendar = Calendar.getInstance();

			CloudSim.init(num_user, calendar, trace_flag);

			PowerContainerAllocationPolicy containerAllocationPolicy = new PowerContainerAllocationPolicySimple();
			PowerContainerVmSelectionPolicy vmSelectionPolicy = new PowerContainerVmSelectionPolicyMaximumUsage();
			HostSelectionPolicy hostSelectionPolicy = new HostSelectionPolicyFirstFit();

			String logAddress = "~/Results";

			hostList = new ArrayList<ContainerHost>();
			hostList = createHostList(WFCConstants.WFC_NUMBER_HOSTS);
			// cloudletList = new ArrayList<ContainerCloudlet>();
			containerList = new ArrayList<Container>();
			// vmList = new ArrayList<ContainerVm>();

			ContainerVmAllocationPolicy vmAllocationPolicy = new PowerContainerVmAllocationPolicyMigrationAbstractHostSelection(
					hostList, vmSelectionPolicy, hostSelectionPolicy,
					WFCConstants.WFC_CONTAINER_OVER_UTILIZATION_THRESHOLD,
					WFCConstants.WFC_CONTAINER_UNDER_UTILIZATION_THRESHOLD);

			WFCDatacenter datacenter = (WFCDatacenter) createDatacenter("datacenter_0",
					PowerContainerDatacenterCM.class, hostList, vmAllocationPolicy, containerList,
					containerAllocationPolicy,
					getExperimentName(experimentName, String.valueOf(WFCConstants.OVERBOOKING_FACTOR)),
					WFCConstants.WFC_DC_SCHEDULING_INTERVAL, logAddress, WFCConstants.WFC_VM_STARTTUP_DELAY,
					WFCConstants.WFC_CONTAINER_STARTTUP_DELAY);

			WFCPlanner wfPlanner = new WFCPlanner("planner_0", 1);

			WFCEngine wfEngine = wfPlanner.getWorkflowEngine();
			// vmList = createVmList(wfEngine.getSchedulerId(0), Parameters.getVmNum());
			// wfEngine.submitVmList(wfEngine.getVmList(), 0);
			wfEngine.bindSchedulerDatacenter(datacenter.getId(), 0);

			CloudSim.terminateSimulation(WFCConstants.SIMULATION_LIMIT);
			CloudSim.startSimulation(); /** main logic will be called from here **/
			CloudSim.stopSimulation();

			List<Job> outputList0 = wfEngine.getJobsReceivedList();

			printJobList(outputList0, datacenter);

			Log.printLine(experimentName + "finished!");
			Log.printLine("Optimal container selection algorithm finished");
			// outputByRunnerAbs();

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("The simulation has been terminated due to an unexpected error");
			Log.printLine(e.getMessage());
			System.exit(0);
		}
	}

	/** Creating data center
	 * @param name
	 * @param datacenterClass
	 * @param hostList
	 * @param vmAllocationPolicy
	 * @param containerList
	 * @param containerAllocationPolicy
	 * @param experimentName
	 * @param schedulingInterval
	 * @param logAddress
	 * @param VMStartupDelay
	 * @param ContainerStartupDelay
	 * @return
	 * @throws Exception
	 */
	public static WFCDatacenter createDatacenter(String name, Class<? extends WFCDatacenter> datacenterClass,
			List<ContainerHost> hostList, ContainerVmAllocationPolicy vmAllocationPolicy, List<Container> containerList,
			ContainerAllocationPolicy containerAllocationPolicy, String experimentName, double schedulingInterval,
			String logAddress, double VMStartupDelay, double ContainerStartupDelay) throws Exception {


		LinkedList<Storage> storageList = new LinkedList<Storage>();
		WFCDatacenter datacenter = null;
		try {
			HarddriveStorage s1 = new HarddriveStorage(name, 1e12);
			s1.setMaxTransferRate(WFCConstants.WFC_DC_MAX_TRANSFER_RATE);
			storageList.add(s1);

			ContainerDatacenterCharacteristics characteristics = new ContainerDatacenterCharacteristics(
					WFCConstants.WFC_DC_ARCH, WFCConstants.WFC_DC_OS, WFCConstants.WFC_DC_VMM, hostList,
					WFCConstants.WFC_DC_TIME_ZONE, WFCConstants.WFC_DC_COST, WFCConstants.WFC_DC_COST_PER_MEM,
					WFCConstants.WFC_DC_COST_PER_STORAGE, WFCConstants.WFC_DC_COST_PER_BW);

			datacenter = new WFCDatacenter(name, characteristics, vmAllocationPolicy, containerAllocationPolicy,
					storageList, schedulingInterval, experimentName, logAddress);

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("The simulation has been terminated due to an unexpected error");
			Log.printLine(e.getMessage());
			System.exit(0);
		}
		return datacenter;
	}
	
	/**Creating containers
	 * @param brokerId
	 * @param containersNumber
	 * @return
	 */
	public static List<Container> createContainerList(int brokerId, int containersNumber) {
		LinkedList<Container> list = new LinkedList<>();
		// peList.add(new ContainerPe(0, new CotainerPeProvisionerSimple((double)mips *
		// ratio)));
		// create VMs
		try {
			Container[] containers = new Container[containersNumber];
			for (int i = 0; i < containersNumber; i++) {

				containers[i] = new PowerContainer(IDs.pollId(Container.class), brokerId,
						(double) WFCConstants.WFC_CONTAINER_MIPS, WFCConstants.WFC_CONTAINER_PES_NUMBER,
						WFCConstants.WFC_CONTAINER_RAM, WFCConstants.WFC_CONTAINER_BW, WFCConstants.WFC_CONTAINER_SIZE,
						WFCConstants.WFC_CONTAINER_VMM,
						// new
						// ContainerCloudletSchedulerTimeShared(),WFCConstants.WFC_DC_SCHEDULING_INTERVAL);
						new ContainerCloudletSchedulerDynamicWorkload(WFCConstants.WFC_CONTAINER_MIPS,
								WFCConstants.WFC_CONTAINER_PES_NUMBER),
						WFCConstants.WFC_DC_SCHEDULING_INTERVAL);
				list.add(containers[i]);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("The simulation has been terminated due to an unexpected error");
			Log.printLine(e.getMessage());
			System.exit(0);
		}
		return list;
	}

	/**Creating VM list
	 * @param brokerId
	 * @param containerVmsNumber
	 * @return
	 */
	public static List<ContainerVm> createVmList(int brokerId, int containerVmsNumber) {
		// Creates a container to store VMs.
		LinkedList<ContainerVm> list = new LinkedList<>();
		ArrayList peList = new ArrayList();

		try {
			for (int p = 0; p < WFCConstants.WFC_NUMBER_VM_PES; p++) {
				peList.add(new ContainerPe(p, new CotainerPeProvisionerSimple(
						(double) WFCConstants.WFC_VM_MIPS * WFCConstants.WFC_VM_RATIO)));
			}
			// create VMs
			ContainerVm[] vm = new ContainerVm[containerVmsNumber];

			for (int i = 0; i < containerVmsNumber; i++) {
				vm[i] = new PowerContainerVm(IDs.pollId(ContainerVm.class), brokerId, WFCConstants.WFC_VM_MIPS,
						(float) WFCConstants.WFC_VM_RAM, WFCConstants.WFC_VM_BW, WFCConstants.WFC_VM_SIZE,
						WFCConstants.WFC_VM_VMM, new ContainerSchedulerTimeSharedOverSubscription(peList),
						// new ContainerSchedulerTimeSharedOverSubscription(peList),
						new ContainerRamProvisionerSimple(WFCConstants.WFC_VM_RAM),
						new ContainerBwProvisionerSimple(WFCConstants.WFC_VM_BW), peList,
						WFCConstants.WFC_DC_SCHEDULING_INTERVAL);

				/*
				 * new ContainerVm(IDs.pollId(ContainerVm.class), brokerId, (double) mips,
				 * (float) ram, bw, size, "Xen", new ContainerSchedulerTimeShared(peList), new
				 * ContainerRamProvisionerSimple(ram), new ContainerBwProvisionerSimple(bw),
				 * peList);
				 */

				// new ContainerVm(i, userId, mips * ratio, pesNumber, ram, bw, size, vmm, new
				// CloudletSchedulerSpaceShared());
				list.add(vm[i]);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("The simulation has been terminated due to an unexpected error");
			Log.printLine(e.getMessage());
			System.exit(0);
		}
		return list;
	}


	public static List<ContainerHost> createHostList(int hostsNumber) {

		ArrayList<ContainerHost> hostList = new ArrayList<ContainerHost>();
		// 2. A Machine contains one or more PEs or CPUs/Cores. Therefore, should
		// create a list to store these PEs before creating
		// a Machine.

		try {
			for (int i = 1; i <= WFCConstants.WFC_NUMBER_HOSTS; i++) {
				ArrayList<ContainerVmPe> peList = new ArrayList<ContainerVmPe>();
				// 3. Create PEs and add these into the list.
				// for a quad-core machine, a list of 4 PEs is required:
				for (int p = 0; p < WFCConstants.WFC_NUMBER_HOST_PES; p++) {
					peList.add(new ContainerVmPe(p, new ContainerVmPeProvisionerSimple(WFCConstants.WFC_HOST_MIPS))); 
				}

				hostList.add(new PowerContainerHostUtilizationHistory(IDs.pollId(ContainerHost.class),
						new ContainerVmRamProvisionerSimple(WFCConstants.WFC_HOST_RAM),
						new ContainerVmBwProvisionerSimple(WFCConstants.WFC_HOST_BW), WFCConstants.WFC_HOST_STORAGE,
						peList, new ContainerVmSchedulerTimeSharedOverSubscription(peList),
						// new ContainerVmSchedulerTimeShared(peList),
						WFCConstants.HOST_POWER[2]));
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("The simulation has been terminated due to an unexpected error");
			Log.printLine(e.getMessage());
			System.exit(0);
		}
		return hostList;
	}

	private static String getExperimentName(String... args) {
		StringBuilder experimentName = new StringBuilder();

		for (int i = 0; i < args.length; ++i) {
			if (!args[i].isEmpty()) {
				if (i != 0) {
					experimentName.append("_");
				}

				experimentName.append(args[i]);
			}
		}

		return experimentName.toString();
	}

	/**
	 * Gets the maximum number of GB ever used by the application's heap.
	 * 
	 * @return the max heap utilization in GB
	 * @see <a href=
	 *      "https://www.oracle.com/webfolder/technetwork/tutorials/obe/java/gc01/index.html">Java
	 *      Garbage Collection Basics (for information about heap space)</a>
	 */
	private static double getMaxHeapUtilizationGB() {
		final double memoryBytes = ManagementFactory.getMemoryPoolMXBeans().stream()
				.filter(bean -> bean.getType() == MemoryType.HEAP)
				.filter(bean -> bean.getName().contains("Eden Space") || bean.getName().contains("Survivor Space"))
				.map(MemoryPoolMXBean::getPeakUsage).mapToDouble(MemoryUsage::getUsed).sum();

		return Conversion.bytesToGigaBytes(memoryBytes);
	}

	
	/**
	 * Prints the job objects
	 *
	 * @param list list of jobs
	 */
	protected static void printJobList(List<Job> list, WFCDatacenter datacenter) {
		double maxHeapUtilizationGB = getMaxHeapUtilizationGB();
		String indent = "    ";
		double cost = 0.0;
		double time = 0.0;
		double length = 0.0;
		int counter = 1;
		int success_counter = 0;
		int failed_counter = 0;

		Log.printLine();
		Log.printLine();	
		DecimalFormat dft0 = new DecimalFormat("###.###");
		DecimalFormat dft = new DecimalFormat("####.###");

		for (Job job : list) {
			
			Log.print(indent);
			cost += job.getProcessingCost();
			time += job.getActualCPUTime();
			length += job.getCloudletLength();

			if (job.getCloudletStatus() == Cloudlet.SUCCESS) {
				success_counter++;
			} 
		}
		Log.printLine();
		Log.printLine("----------------------------------------");
		Log.printLine("The total failed counter is " + dft.format(failed_counter));
		Log.printLine("The total success counter is " + dft.format(success_counter));

	}
}


