/**
 * 
 */
package org.cloudbus.cloudsim.container.resourceAllocators;


import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.wfc.core.Containers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *  @author Priyanka Movva R
 * @author Arpitha Hiresadrahalli Dayananda
 *
 */
public class ContainerAllocationPolicySimple extends ContainerAllocationPolicy {
	/** The vm table. */
	private Map<String, ContainerVm> containerVmTable;

	/** The used pes. */
	private Map<String, Integer> usedPes;

	/** The free pes. */
	private List<Integer> freePes;
	
	public static final Integer threshold = 2;
	
	/**
	 * Creates the new VmAllocationPolicySimple object.
	 *
	 * @pre $none
	 * @post $none
	 */
	public ContainerAllocationPolicySimple() {
		super();
		setFreePes(new ArrayList<Integer>());
		setContainerVmTable(new HashMap<String, ContainerVm>());
		setUsedPes(new HashMap<String, Integer>());
	}


	/**
	 * Implementation of threshold based serverless container resource allocation.
	 */
	@Override
	public boolean allocateVmForContainer(Container container, List<ContainerVm> containerVmList) {
//		the available container list is updated. It gets is from the data center.
		setContainerVmList(containerVmList);
		for (ContainerVm containerVm : getContainerVmList()) {
			getFreePes().add(containerVm.getNumberOfPes());

		}
		int requiredPes = container.getNumberOfPes();
		boolean result = false;
		int tries = 0;
		List<Integer> freePesTmp = new ArrayList<>();
		for (Integer freePes : getFreePes()) {
			freePesTmp.add(freePes);
		}

		if (!getContainerVmTable().containsKey(container.getUid())) { // if this vm was not created
			do {// we still trying until we find a host or until we try all of them
				//threshold computation of each container
				double moreFree = getThreshold(container);
				int idx = -1;

				// to allocate container threshold comparison with vm's pes.
				for (int i = 0; i < freePesTmp.size(); i++) {
					if (freePesTmp.get(i) > moreFree) {
						moreFree = freePesTmp.get(i);
						idx = i;
					}
				}

				ContainerVm containerVm = getContainerVmList().get(idx);
				result = containerVm.containerCreate(container);

				if (result) { // if vm were succesfully created in the host
					getContainerVmTable().put(container.getUid(), containerVm);
					getUsedPes().put(container.getUid(), requiredPes);
					getFreePes().set(idx, getFreePes().get(idx) - requiredPes);
					result = true;
					Log.formatLine(
							" Container #" + container.getId() + " has been allocated to the Vm #" + containerVm.getId());
					break;
				} else {
					freePesTmp.set(idx, Integer.MIN_VALUE);
				}
				tries++;
			} while (!result && tries < getFreePes().size());

		}

		freePesTmp.clear();
		return result;
	}

	/**
	 * @param container
	 * @return
	 */
	private double getThreshold(Container container) {
		
		 double threshold = (container.getThresholdRate() * (1-container.getNormWorkLoadRate()) * container.getMips() * container.getMaxWorkLoad());
		
		return threshold;
	}


	@Override
	public boolean allocateVmForContainer(Container container, ContainerVm containerVm) {
		if (containerVm.containerCreate(container)) { // if vm has been succesfully created in the host
			getContainerVmTable().put(container.getUid(), containerVm);

			int requiredPes = container.getNumberOfPes();
			int idx = getContainerVmList().indexOf(container);
			getUsedPes().put(container.getUid(), requiredPes);
			getFreePes().set(idx, getFreePes().get(idx) - requiredPes);

			Log.formatLine(
					" Container #" + container.getId() + " has been allocated to the Vm #" + containerVm.getId());
			return true;
		}

		
		return false;
	}


	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Container> containerList) {
		return null;
	}

	@Override
	public void deallocateVmForContainer(Container container) {

		ContainerVm containerVm = getContainerVmTable().remove(container.getUid());
		int idx = getContainerVmList().indexOf(containerVm);
		int pes = getUsedPes().remove(container.getUid());
		if (containerVm != null) {
			containerVm.containerDestroy(container);
			getFreePes().set(idx, getFreePes().get(idx) + pes);
		}

	}

	@Override
	public ContainerVm getContainerVm(Container container) {
		return getContainerVmTable().get(container.getUid());
	}

	@Override
	public ContainerVm getContainerVm(int containerId, int userId) {
		return getContainerVmTable().get(Container.getUid(userId, containerId));
	}

	protected Map<String, ContainerVm> getContainerVmTable() {
		return containerVmTable;
	}

	protected void setContainerVmTable(Map<String, ContainerVm> containerVmTable) {
		this.containerVmTable = containerVmTable;
	}

	protected Map<String, Integer> getUsedPes() {
		return usedPes;
	}

	protected void setUsedPes(Map<String, Integer> usedPes) {
		this.usedPes = usedPes;
	}

	protected List<Integer> getFreePes() {
		return freePes;
	}

	protected void setFreePes(List<Integer> freePes) {
		this.freePes = freePes;
	}
	
	
	/** Implementation of Optimal container resource allocation strategy.
	 * @param container
	 * @param containerVmList
	 */
	public void optimalNodeSelectionAlgorithm(Containers container, List<ContainerVm> containerVmList) {
	
		// attributes fetching and computing
		double info = container.getvCpuUtilization() + container.getvMemoryUtilization();	
		double cmin = container.getContainerCpuUtilization() + container.getContainerMemoryUtilization();		
		
		//sorting containers based on load.
		container.sortBasedOnLoad();
		
		//Allocate container to VM
		if(cmin >= info) {
			container.allocateContainersToVm();
		}
		
		// check for next available VM
		if(container.checkForNextAvailableVM(cmin,info)) {
			container.allocateToNextAvailableVM();		
		}
		
	}


	
}
