# thingworx-async-tasks
[**Unofficial/Not Supported**] Contains the code of the ThingWorx AsyncTasks Extension built on Extension SDK version 9.4 (not provided here).

**This Extension is provided as-is and without warranty or support. It is not part of the PTC product suite. This project is licensed under the terms of the MIT license.**

Considering the statement above, in case you have any questions regarding this extension:
- do **not** open PTC Technical Support tickets for this extension (since it is not part of the PTC product suite)

This is an Ant Eclipse (Version: 2023-12 (4.30.0)) project that uses the ThingWorx Eclipse plugin, available in the support.ptc.com portal under the ThingWorx platform downloads.
## How to use in Eclipse (to extend/fix its functionality)
1. Install the ThingWorx Eclipse plugin v 9.0.1.202102191527
2. Download the ThingWorx Extension SDK version 9.4. It should be a zip file.
3. Import the project: Use the File Menu / Import / Projects from Folder or Archive.
4. After import, right click on the project name in the left hand side of the screen and in the ThingWorx Extension tab setup the path to the Extension SDK zip file.
5. Execute the ant build task. The project uses a customized version of the ant build script that is generated by the ThingWorx Eclipse plugin.

This is a CompletableFuture Java wrapper for ThingWorx, which allows executing multiple tasks asynchronously and accessing their result in the same service in a similar style as of Javascript Promise.

The main usecase of this extension is to allow parallel execution of logic (encapsulated as a ThingWorx service) and retrieving the result of the Tasks executed in this manner in the scope of the original service.

The execution time of the ProcessTasks service below = the execution time of the slowest Task supplied to it.


## How to Use in ThingWorx
1. Download the extension from the Releases section.
2. Install the extension in ThingWorx
3. Assign the **AsyncProcessing** ThingShape to any Thing or ThingTemplate. This will expose access to the services **ProcessTasks** and ***XProcessTasks*** (X for extended).
   ### Simple Process Tasks ###
4. Process Tasks service takes as input a list of Tasks (1 Task = 1 combination of ThingName, ServiceName and InputParameters): <br>
  4.1 Create an infotable (PTC.AT.Input.Datashape) of Tasks: <br>
  
  ```
let tasks = DataShapes["PTC.AT.Input.Datashape"].CreateValues();
tasks.AddRow({
    Service: "SlowService1", // STRING
    InputParameters: "{\"message\":\"Message from service wrapper for service 1\"}", // STRING
    ThingName: "testhing" // THINGNAME
});
tasks.AddRow({
    Service: "SlowService2", // STRING
    InputParameters: "{\"message\":\"Message from service wrapper for service 2\"}", // STRING
    ThingName: "testhing" // THINGNAME
});
```
<br>
Note: 

- Input parameters are passed in a custom JSON format:**{paramName: value}**, where paramName is the name of the called service input parameter.
- Multiple parameters are supported: **{param1: value1, param2: value2}** etc.
- **the only input parameter type supported is STRING** (if you want to pass Infotables, convert to JSON, then string and pass as such)
<br>

  4.2 Execute the tasks by executing ProcessTasks with the infotable above as input: <br>

  ```
result = me.ProcessTasks({
	Tasks: tasks /* INFOTABLE {"dataShape":"PTC.AT.Input.Datashape"} */
});
```

<br>

  4.3. Retrieve the result from the infotable (PTC.AT.OutputDatashape)

  ![image](https://github.com/vrosu/thingworx-async-tasks/assets/11868471/356d7b32-90ef-4c5f-ba44-5f654c003593)

Note:

  - The only Output parameter type is STRING (if you want to pass an Infotable, convert to JSON, then string and pass as such)

<br>

### Extended Process Tasks ###

5. Extended Process Tasks service takes as input a list of Tasks (1 Task = 1 combination of ThingName, ServiceName and InputParameters): <br>
  5.1 Create an infotable (PTC.AT.XInput.Datashape) of Tasks, and before Input Parameters of different types. See full example as follows: <br>
  
  ```
let inputParams1 = DataShapes["PTC.AT.XInputParameters.Datashape"].CreateValues();
inputParams1.AddRow({
    Name: "name", //STRING
    BaseType: "STRING", //STRING
    Value: "Bud"
});
inputParams1.AddRow({
    Name: "waitingTime", //STRING
    BaseType: "LONG", //STRING
    Value: "5000"
});

let inputParams2 = DataShapes["PTC.AT.XInputParameters.Datashape"].CreateValues();
inputParams2.AddRow({
    Name: "name", //STRING
    BaseType: "STRING", //STRING
    Value: "Terence"
});
inputParams2.AddRow({
    Name: "waitingTime", //STRING
    BaseType: "LONG", //STRING
    Value: "3000"
});

let inputParams3 = DataShapes["PTC.AT.XInputParameters.Datashape"].CreateValues();
inputParams3.AddRow({
    Name: "a", //STRING
    BaseType: "INTEGER", //STRING
    Value: "9"
});
inputParams3.AddRow({
    Name: "b", //STRING
    BaseType: "INTEGER", //STRING
    Value: "6"
});
inputParams3.AddRow({
    Name: "waitingTime", //STRING
    BaseType: "LONG", //STRING
    Value: "2000"
});

let names = DataShapes["GenericStringList"].CreateValues();
names.AddRow({item: "alessio"});
names.AddRow({item: "amro"});
names.AddRow({item: "roman"});
names.AddRow({ item: "vladimir" });

let inputParams5 = DataShapes["PTC.AT.XInputParameters.Datashape"].CreateValues();
inputParams5.AddRow({
  Name: "names", //STRING
  BaseType: "INFOTABLE", //STRING
  ComplexValue: names,
});

let tasks = DataShapes["PTC.AT.XInput.Datashape"].CreateValues();
tasks.AddRow({
  Service: "Service1", // STRING
  InputParameters: inputParams1, // INFOTABLE
  ThingName: "AsyncProcessing.Thing", // THINGNAME 
  OutputBaseType: "STRING" //STRING
});
tasks.AddRow({
  Service: "Service2", // STRING
  InputParameters: inputParams3, // INFOTABLE
  ThingName: "AsyncProcessing.Thing", // THINGNAME
  OutputBaseType: "INTEGER" //STRING
});
//Service 3 has by default waiting time of 10000
tasks.AddRow({
  Service: "Service3", // STRING
  ThingName: "AsyncProcessing.Thing", // THINGNAME
  OutputBaseType: "NOTHING", //NOTHING
});
// Service 4 also has by default waiting time of 10000
tasks.AddRow({
  Service: "Service4", // STRING
  ThingName: "AsyncProcessing.Thing", // THINGNAME
  OutputBaseType: "INFOTABLE", //INFOTABLE
});

tasks.AddRow({
  Service: "Service5", // STRING
  InputParameters: inputParams5, // INFOTABLE
  ThingName: "AsyncProcessing.Thing", // THINGNAME
  OutputBaseType: "INFOTABLE", //INFOTABLE
});

result = me.XProcessTasks({
  Tasks: tasks /* INFOTABLE {"dataShape":"PTC.AT.XInput.Datashape"} */
});
```
<br>
Note: 

- Input parameters are passed in a custom JSON format:**{paramName: value}**, where paramName is the name of the called service input parameter.
- Multiple parameters are supported: **{param1: value1, param2: value2}** etc.
- **the only input parameter type supported is STRING** (if you want to pass Infotables, convert to JSON, then string and pass as such)
<br>

  5.2 Execute the tasks by executing ProcessTasks with the infotable above as input: <br>

  ```
result = me.ProcessTasks({
	Tasks: tasks /* INFOTABLE {"dataShape":"PTC.AT.Input.Datashape"} */
});
```

<br>

  5.3. Retrieve the result from the infotable (PTC.AT.OutputDatashape)

  ![image](<TODO img url>)

Note:

  - The only Output parameter type is STRING (if you want to pass an Infotable, convert to JSON, then string and pass as such)

<br>

**This Extension is provided as-is and without warranty or support. It is not part of the PTC product suite**. This project is licensed under the terms of the MIT license.
