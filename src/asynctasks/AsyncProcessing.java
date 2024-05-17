/**
 * 
 */
package asynctasks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thingworx.data.util.InfoTableInstanceFactory;
import com.thingworx.entities.utils.EntityUtilities;
import com.thingworx.entities.utils.ThingUtilities;
import com.thingworx.logging.LogUtilities;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.relationships.RelationshipTypes.ThingworxRelationshipTypes;
import com.thingworx.security.context.SecurityContext;
import com.thingworx.things.Thing;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.BooleanPrimitive;
import com.thingworx.types.primitives.IPrimitiveType;
import com.thingworx.types.primitives.InfoTablePrimitive;
import com.thingworx.types.primitives.IntegerPrimitive;
import com.thingworx.types.primitives.LongPrimitive;
import com.thingworx.types.primitives.NumberPrimitive;
import com.thingworx.types.primitives.StringPrimitive;
import com.thingworx.webservices.context.ThreadLocalContext;

/**
 * ThingShape that is designed
 * 
 */
public class AsyncProcessing {
	private static Logger _logger = LogUtilities.getInstance().getApplicationLogger(AsyncProcessing.class);

	/**
	* 
	*/
	public AsyncProcessing() {
	}

	@SuppressWarnings("rawtypes")
	@ThingworxServiceDefinition(name = "XProcessTasks", description = "", category = "", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "Result", description = "", baseType = "INFOTABLE", aspects = {
			"isEntityDataShape:true", "dataShape:PTC.AT.XOutput.Datashape" })
	public InfoTable ProcessTasks(
			@ThingworxServiceParameter(name = "Tasks", description = "Extended Processing of Tasks. Supporting all types for input parameters and output parameters.", baseType = "INFOTABLE", aspects = {
					"isRequired:true", "isEntityDataShape:true", "dataShape:PTC.AT.XInputDatashape" }) InfoTable Tasks)
			throws Exception {
		_logger.trace("Entering Service: XProcessTasks");
		// 1. Create an output Infotable as results placeholder
		InfoTable iftbl_TaskOutput = InfoTableInstanceFactory.createInfoTableFromDataShape("PTC.AT.XOutput.Datashape");

		// 2. Record the current security context to pass to the Completable Future, as
		// they don't inherit the current context
		SecurityContext currentSecurityCtx = ThreadLocalContext.getSecurityContext();

		Integer intTaskCount = Tasks.getRowCount();

		if (intTaskCount != 0) {

			// create a new ArrayList of CompletableFuture as placeholder for all Tasks
			// supplied in the Tasks infotable
			ArrayList<CompletableFuture<ValueCollection>> tasks = new ArrayList<CompletableFuture<ValueCollection>>();

			// 3. Loop through the Task list and create a Completable Future object for each task
			for (ValueCollection row : Tasks.getRows()) {

				// 3.1 Iterate through the current Task attributes and store them in local variables
				ValueCollection serviceParameters = new ValueCollection();

				InfoTable itp = InfoTableInstanceFactory
						.createInfoTableFromDataShape("PTC.AT.XInputParameters.Datashape");

				String strThingName = row.getStringValue("ThingName");
				String strServiceName = row.getStringValue("Service");
				String outputBaseType = row.getStringValue("OutputBaseType");
				String outputEventTopic = row.getStringValue("OutputEventTopic");

				//3.2 If available, handle input parameters
				if (row.has("InputParameters")) {
					InfoTable itInputParameters = (InfoTable) row.getPrimitive("InputParameters").getValue();

					for (ValueCollection parameter : itInputParameters.getRows()) {
						String paramName = parameter.getStringValue("Name");
						String paramBaseType = parameter.getStringValue("BaseType");

						IPrimitiveType serviceParamValue;

						if (paramBaseType.equals("INFOTABLE")) {
							InfoTable paramComplexValue = (InfoTable) parameter.getValue("ComplexValue");
							serviceParamValue = new InfoTablePrimitive(paramComplexValue);
						} else {
							String paramValue = parameter.getStringValue("Value");
							serviceParamValue = extractValue(paramName, paramBaseType, paramValue);
						}

						if (serviceParamValue != null /* && serviceParameters.size()>0 */ ) { // null = BaseType
																								// NOTHING, otherwise
																								// exception is thrown
							serviceParameters.put(paramName, serviceParamValue);
							itp.addRow(parameter);
						}

					}

				}

				//Note: at this stage all input parameters are ready
				//3.3 CompletableFuture with return type of ValueCollection, as needed by the info table at Step 1
				CompletableFuture<ValueCollection> future = CompletableFuture.supplyAsync(() -> {
					// 3.3.1 We create the ValueCollection (the result row) that will be added in info table at Step 1
					ValueCollection vc = new ValueCollection();

					// 3.3.2. Set the Security Context to the current extension security Context
					// since this will not execute in the same thread
					ThreadLocalContext.setSecurityContext(currentSecurityCtx);

					// 3.3.3 Find the Thing specified in the ThingName parameter at step 3.1
					Thing thing = ThingUtilities.findThing(strThingName);

					InfoTable iftbl_Result;
					try {
						// 3.3.4. If the service has no input parameters we pass a null
						iftbl_Result = (serviceParameters.size() == 0)
								? thing.processServiceRequest(strServiceName, null)
								: thing.processServiceRequest(strServiceName, serviceParameters);
						
						// 3.3.5. We no longer need a ThingWorx security context here, so we clear it.
						ThreadLocalContext.clearSecurityContext();

						vc.put("ThingName", new StringPrimitive(strThingName));
						vc.put("Service", new StringPrimitive(strServiceName));

						vc.put("InputParameters", new InfoTablePrimitive(itp));
						vc.put("OutputBaseType", new StringPrimitive(outputBaseType));
						vc.put("OutputEventTopic", new StringPrimitive(outputEventTopic));
						
						//3.3.6 Return based on outputBaseType complex response or simple one.
						if (iftbl_Result != null && iftbl_Result.getRowCount() > 0) {
							if (outputBaseType.equals("INFOTABLE")) {
								vc.put("OutputComplexResult", new InfoTablePrimitive(iftbl_Result));
							} else {
								IPrimitiveType result = extractResult(iftbl_Result, outputBaseType);
								vc.put("OutputResult", result);
							}

						}

					} catch (Exception e) {
						_logger.error("Thing: " + strThingName + "| Service: " + strServiceName + " | Message: "
								+ e.getMessage());
					}
					return vc;
				});
				tasks.add(future);
				_logger.trace("Exiting Service: XProcessTasks");
			}

			// 4. All Tasks have been added to the ArrayList, it's now time to execute them.
			// This is the step that will block for an amount of time equal to the slowest
			// task execution.
			CompletableFuture.allOf(tasks.toArray(new CompletableFuture[intTaskCount])).thenRun(() -> {
				try {
					// 4.1 Each task is being executed in parallel and once finished it will add its
					// string result to the info table at Step 1
					for (CompletableFuture<ValueCollection> completableFuture : tasks) {
						iftbl_TaskOutput.addRow(completableFuture.get());
					}
				} catch (InterruptedException e) {
					_logger.error("Interrupted exception: " + e.getStackTrace().toString());
				} catch (ExecutionException e) {
					_logger.error("Execution exception: " + e.getStackTrace().toString());
				}
			}).join();
		}

		//4.2 Finally return info table of tasks.
		return iftbl_TaskOutput;
	}

	@SuppressWarnings({ "rawtypes", "deprecation" })
	private IPrimitiveType extractResult(InfoTable iftbl_Result, String outputBaseType) throws Exception {

		Object resultObj = iftbl_Result.getRow(0).getValue("result");

		switch (outputBaseType) {
		case "BOOLEAN":
			return new BooleanPrimitive((Boolean) resultObj);
		case "DOUBLE":
			return new NumberPrimitive((Double) resultObj);
		case "INTEGER":
			return new IntegerPrimitive((Integer) resultObj);
		case "LONG":
			return new LongPrimitive((Long) resultObj);
		case "STRING":
			return new StringPrimitive(resultObj.toString());
//			break;
		default: // "NOTHING"
			String errMsg = AsyncProcessing.class.getSimpleName() + " output has unknown base type " + outputBaseType
					+ ".";
			_logger.error(errMsg);
			throw new IllegalArgumentException(errMsg);
		}

	}

	@SuppressWarnings({ "rawtypes", "deprecation" })
	private IPrimitiveType extractValue(String paramName, String paramBaseType, String paramValue) {
		switch (paramBaseType) {
		case "BOOLEAN":
			return new BooleanPrimitive(Boolean.parseBoolean(paramValue));
		case "DOUBLE":
			return new NumberPrimitive(Double.parseDouble(paramValue));
		case "INTEGER":
			return new IntegerPrimitive(Integer.parseInt(paramValue));
		case "LONG":
			return new LongPrimitive(Long.parseLong(paramValue));
		case "STRING":
			return new StringPrimitive(paramValue);
		default: // "NOTHING"
			String errMsg = AsyncProcessing.class.getSimpleName() + " Parameter " + paramName
					+ " has assigned unknown base type " + paramBaseType + ".";
			_logger.error(errMsg);
			throw new IllegalArgumentException(errMsg);
		}

	}

}
