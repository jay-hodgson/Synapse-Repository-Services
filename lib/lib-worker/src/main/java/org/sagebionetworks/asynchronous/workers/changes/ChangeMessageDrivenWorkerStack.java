package org.sagebionetworks.asynchronous.workers.changes;

import org.sagebionetworks.database.semaphore.CountingSemaphore;
import org.sagebionetworks.workers.util.aws.message.MessageDrivenWorkerStack;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

/**
 * A worker stack driven by a Synapse change message.
 * 
 * @author jhill
 *
 */
public class ChangeMessageDrivenWorkerStack implements Runnable {

	MessageDrivenWorkerStack stack;

	public ChangeMessageDrivenWorkerStack(CountingSemaphore semaphore,
			AmazonSQSClient awsSQSClient, AmazonSNSClient awsSNSClient,
			ChangeMessageDrivenWorkerStackConfig config) {
		// Get the configured runner.
		ChangeMessageDrivenRunner changeRunner = config.getRunner();
		if(changeRunner instanceof LockTimeoutAware){
			LockTimeoutAware lockAware = (LockTimeoutAware) changeRunner;
			// forward the lock timeout to the runner.
			lockAware.setTimeoutSeconds(config.getConfig().getSemaphoreGatedRunnerConfiguration().getLockTimeoutSec());
		}
		// Wrap the runner in a processor that converts batches of change
		// messages into single messages.
		ChangeMessageBatchProcessor batchProcessor = new ChangeMessageBatchProcessor(
				awsSQSClient, config.getConfig().getMessageQueueConfiguration()
						.getQueueName(), changeRunner);
		config.getConfig().setRunner(batchProcessor);
		stack = new MessageDrivenWorkerStack(semaphore, awsSQSClient,
				awsSNSClient, config.getConfig());
	}

	@Override
	public void run() {
		stack.run();
	}

}
