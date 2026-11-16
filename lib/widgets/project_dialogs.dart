part of '../main.dart';

Future<MonitorProject?> showProjectDialog(
  BuildContext context, {
  MonitorProject? project,
}) async {
  final nameController = TextEditingController(text: project?.name ?? '');
  final cityController = TextEditingController(text: project?.city ?? '');
  final mainMeituanController = TextEditingController(
    text: project?.mainHotel.meituanName ?? '',
  );
  final mainCtripController = TextEditingController(
    text: project?.mainHotel.ctripName ?? '',
  );
  final competitorMeituanController = TextEditingController();
  final competitorCtripController = TextEditingController();
  var mainSameName =
      project == null ||
      project.mainHotel.meituanName == project.mainHotel.ctripName;
  var competitorSameName = true;

  final competitors = List<HotelConfig>.from(
    project?.competitors ?? <HotelConfig>[],
  );

  void addCompetitor(void Function(void Function()) setDialogState) {
    final meituanName = competitorMeituanController.text.trim();
    final ctripName = competitorSameName
        ? meituanName
        : competitorCtripController.text.trim();

    if (meituanName.isEmpty && ctripName.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('美团名称和携程名称至少填写一个')));
      return;
    }

    FocusManager.instance.primaryFocus?.unfocus();
    setDialogState(() {
      competitors.add(
        HotelConfig.create(meituanName: meituanName, ctripName: ctripName),
      );
      competitorMeituanController.clear();
      competitorCtripController.clear();
    });
  }

  Future<void> addHotelsInBatch(
    void Function(void Function()) setDialogState,
  ) async {
    final imported = await showBatchHotelDialog(context);
    if (imported == null || !context.mounted) return;

    setDialogState(() {
      final mainHotel = imported.mainHotel;
      if (mainHotel != null) {
        mainMeituanController.text = mainHotel.meituanName;
        mainCtripController.text = mainHotel.ctripName;
        mainSameName =
            mainHotel.meituanName.isNotEmpty &&
            mainHotel.meituanName == mainHotel.ctripName;
      }
      competitors.addAll(imported.competitors);
    });

    final parts = <String>[
      if (imported.mainHotel != null) '主体酒店 1 家',
      if (imported.competitors.isNotEmpty)
        '竞对酒店 ${imported.competitors.length} 家',
    ];
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text('已导入${parts.join('、')}，保存项目后生效')));
  }

  final result = await showDialog<MonitorProject>(
    context: context,
    builder: (context) {
      return StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            title: Row(
              children: [
                Expanded(child: Text(project == null ? '新增监控项目' : '编辑监控项目')),
                const SizedBox(width: 8),
                OutlinedButton.icon(
                  key: const Key('batchAddHotelsButton'),
                  onPressed: () => addHotelsInBatch(setDialogState),
                  icon: const Icon(Icons.playlist_add, size: 20),
                  label: const Text('批量添加'),
                ),
              ],
            ),
            content: SingleChildScrollView(
              child: SizedBox(
                width: 420,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: nameController,
                      decoration: const InputDecoration(
                        labelText: '项目名称',
                        hintText: '例如：上海酒店竞品监控',
                      ),
                    ),
                    TextField(
                      controller: cityController,
                      decoration: const InputDecoration(
                        labelText: '城市',
                        hintText: '例如：上海',
                      ),
                    ),
                    const SizedBox(height: 12),
                    const Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        '主体酒店',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                    const Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        '主体酒店可只填写一个平台，留空的平台会自动跳过',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                    ),
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      value: mainSameName,
                      title: const Text('美团与携程名称相同'),
                      controlAffinity: ListTileControlAffinity.leading,
                      onChanged: (value) {
                        FocusManager.instance.primaryFocus?.unfocus();
                        setDialogState(() {
                          mainSameName = value ?? false;
                        });
                      },
                    ),
                    TextField(
                      controller: mainMeituanController,
                      decoration: InputDecoration(
                        labelText: mainSameName ? '酒店名称' : '美团名称',
                        hintText: mainSameName ? '请输入酒店完整名称' : '请输入美团中的完整酒店名称',
                      ),
                    ),
                    if (!mainSameName)
                      TextField(
                        controller: mainCtripController,
                        decoration: const InputDecoration(
                          labelText: '携程名称',
                          hintText: '请输入携程中的完整酒店名称',
                        ),
                      ),
                    const SizedBox(height: 16),
                    const Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        '竞对酒店',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                    const Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        '竞对酒店可只填写一个平台，留空的平台会自动跳过',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: competitorMeituanController,
                      decoration: InputDecoration(
                        labelText: competitorSameName ? '竞对酒店名称' : '竞对酒店美团名称',
                      ),
                    ),
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      value: competitorSameName,
                      title: const Text('美团与携程名称相同'),
                      controlAffinity: ListTileControlAffinity.leading,
                      onChanged: (value) {
                        FocusManager.instance.primaryFocus?.unfocus();
                        setDialogState(() {
                          competitorSameName = value ?? false;
                        });
                      },
                    ),
                    if (!competitorSameName)
                      TextField(
                        controller: competitorCtripController,
                        decoration: const InputDecoration(
                          labelText: '竞对酒店携程名称',
                        ),
                        onSubmitted: (_) => addCompetitor(setDialogState),
                      ),
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton.icon(
                        onPressed: () => addCompetitor(setDialogState),
                        icon: const Icon(Icons.add_circle),
                        label: const Text('添加竞对酒店'),
                      ),
                    ),
                    const SizedBox(height: 10),
                    if (competitors.isEmpty)
                      const Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          '暂未添加竞对酒店',
                          style: TextStyle(color: Colors.grey),
                        ),
                      ),
                    ...competitors.asMap().entries.map((entry) {
                      final index = entry.key;
                      final hotel = entry.value;

                      return ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.hotel_outlined),
                        title: Text(hotel.displayName),
                        subtitle: Text(
                          '美团：${hotel.meituanName.isEmpty ? '未填写（跳过）' : hotel.meituanName}\n'
                          '携程：${hotel.ctripName.isEmpty ? '未填写（跳过）' : hotel.ctripName}',
                        ),
                        trailing: IconButton(
                          icon: const Icon(Icons.close),
                          tooltip: '删除竞对酒店',
                          onPressed: () {
                            setDialogState(() {
                              competitors.removeAt(index);
                            });
                          },
                        ),
                      );
                    }),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () {
                  final name = nameController.text.trim();
                  final city = cityController.text.trim();
                  final mainMeituanName = mainMeituanController.text.trim();
                  final mainCtripName = mainSameName
                      ? mainMeituanName
                      : mainCtripController.text.trim();

                  if (name.isEmpty || city.isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('项目名称和城市不能为空')),
                    );
                    return;
                  }

                  if (mainMeituanName.isEmpty && mainCtripName.isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('主体酒店的美团名称和携程名称至少填写一个')),
                    );
                    return;
                  }

                  Navigator.pop(
                    context,
                    MonitorProject(
                      name: name,
                      city: city,
                      mainHotel: HotelConfig(
                        id:
                            project?.mainHotel.id ??
                            'hotel_${DateTime.now().microsecondsSinceEpoch}',
                        meituanName: mainMeituanName,
                        ctripName: mainCtripName,
                      ),
                      competitors: List<HotelConfig>.from(competitors),
                      meituanSelected: project?.meituanSelected ?? true,
                      ctripSelected: project?.ctripSelected ?? true,
                      createdAt: project?.createdAt,
                      todayViewCount: project?.todayViewCount ?? 0,
                      lastViewDate: project?.lastViewDate,
                      meituanReports: project?.meituanReports,
                      ctripReports: project?.ctripReports,
                    ),
                  );
                },
                child: const Text('保存'),
              ),
            ],
          );
        },
      );
    },
  );

  // Wait until the dialog route and its focused fields are fully unmounted.
  WidgetsBinding.instance.addPostFrameCallback((_) {
    nameController.dispose();
    cityController.dispose();
    mainMeituanController.dispose();
    mainCtripController.dispose();
    competitorMeituanController.dispose();
    competitorCtripController.dispose();
  });

  return result;
}

Future<BatchHotelImport?> showBatchHotelDialog(BuildContext context) async {
  final controller = TextEditingController();
  String? formatError;

  final result = await showDialog<BatchHotelImport>(
    context: context,
    builder: (context) {
      return StatefulBuilder(
        builder: (context, setDialogState) {
          return Dialog.fullscreen(
            child: Scaffold(
              key: const Key('batchFullScreenEditor'),
              backgroundColor: Colors.white,
              appBar: AppBar(
                leading: IconButton(
                  tooltip: '返回',
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close),
                ),
                title: const Text('批量添加酒店'),
                actions: [
                  Padding(
                    padding: const EdgeInsets.only(right: 12),
                    child: FilledButton(
                      key: const Key('confirmBatchAddButton'),
                      onPressed: () {
                        try {
                          final imported = parseBatchHotels(controller.text);
                          Navigator.pop(context, imported);
                        } on BatchHotelFormatException catch (error) {
                          setDialogState(() => formatError = error.message);
                        }
                      },
                      child: const Text('添加'),
                    ),
                  ),
                ],
              ),
              body: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                child: TextField(
                  key: const Key('batchHotelInput'),
                  controller: controller,
                  expands: true,
                  minLines: null,
                  maxLines: null,
                  keyboardType: TextInputType.multiline,
                  textAlignVertical: TextAlignVertical.top,
                  decoration: InputDecoration(
                    hintText: batchHotelTemplate,
                    hintStyle: const TextStyle(
                      color: Colors.black45,
                      fontSize: 15,
                      height: 1.45,
                    ),
                    errorText: formatError,
                    errorMaxLines: 3,
                    filled: true,
                    fillColor: const Color(0xFFF4F7F9),
                    contentPadding: const EdgeInsets.all(18),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide.none,
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: const BorderSide(
                        color: Color(0xFF176B87),
                        width: 1.5,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          );
        },
      );
    },
  );

  WidgetsBinding.instance.addPostFrameCallback((_) {
    controller.dispose();
  });
  return result;
}

void showReportDialog(
  BuildContext context, {
  required String title,
  required ReportData report,
}) {
  showDialog<void>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: Text(title),
        content: SingleChildScrollView(
          child: ReportCard(report: report, isPrevious: true),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('关闭'),
          ),
        ],
      );
    },
  );
}

String formatDateTime(DateTime time) {
  String two(int value) => value.toString().padLeft(2, '0');

  return '${time.year}-${two(time.month)}-${two(time.day)} '
      '${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
}
