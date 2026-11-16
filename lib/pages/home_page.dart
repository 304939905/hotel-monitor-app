part of '../main.dart';

class HomePage extends StatefulWidget {
  final ProjectStorage storage;

  const HomePage({super.key, this.storage = const ProjectStorage()});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  static const automationService = AndroidAutomationService();
  List<MonitorProject> projects = [];
  bool loading = true;
  bool batchRunning = false;
  String? loadError;
  List<Map<String, String>>? pendingCtripTasks;
  List<Map<String, String>>? pendingMeituanTasks;
  bool resumingPendingBatch = false;
  Map<String, dynamic>? captchaPendingState;
  bool captchaResumeStarting = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    loadProjects();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      syncBatchCaptures();
      refreshCaptchaPendingState();
      resumePendingBatch();
    }
  }

  Future<void> refreshCaptchaPendingState() async {
    final pending = await automationService.getCaptchaPendingState();
    if (!mounted) return;
    setState(() => captchaPendingState = pending);
  }

  Future<void> resumeCaptchaPendingTasks() async {
    if (captchaResumeStarting) return;
    final connected = await automationService.isAccessibilityConnected();
    if (!mounted) return;
    if (!connected) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('自动化服务未连接，请重新开启无障碍权限')));
      return;
    }
    if (!await automationService.isScreenCaptureReady()) {
      await automationService.requestScreenCapture();
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请允许屏幕截图，然后再次点击“继续未完成任务”')));
      return;
    }

    setState(() {
      captchaResumeStarting = true;
      batchRunning = true;
    });
    final started = await automationService.resumeCaptchaPending();
    if (!mounted) return;
    setState(() {
      captchaResumeStarting = false;
      if (started) captchaPendingState = null;
      if (!started) batchRunning = false;
    });
    if (!started) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('未能继续任务，请确认验证码已经完成并重试')));
    }
  }

  Future<void> discardCaptchaPendingTasks() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('放弃未完成任务？'),
        content: const Text('已经采集完成的结果会保留，验证码之后尚未执行的任务将被清除。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('确认放弃'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final cleared = await automationService.clearCaptchaPending();
    if (!mounted) return;
    if (cleared) setState(() => captchaPendingState = null);
  }

  Future<void> resumePendingBatch() async {
    if (pendingMeituanTasks == null || resumingPendingBatch) return;
    resumingPendingBatch = true;
    await Future<void>.delayed(const Duration(milliseconds: 700));
    final ready = await automationService.isScreenCaptureReady();
    if (!mounted) return;
    if (!ready) {
      pendingMeituanTasks = null;
      pendingCtripTasks = null;
      resumingPendingBatch = false;
      setState(() => batchRunning = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('未获得屏幕截图授权，采集未开始')));
      return;
    }
    final ctripTasks = pendingCtripTasks ?? const <Map<String, String>>[];
    final meituanTasks = pendingMeituanTasks!;
    pendingCtripTasks = null;
    pendingMeituanTasks = null;
    final started = ctripTasks.isNotEmpty && meituanTasks.isNotEmpty
        ? await automationService.startCombinedBatch(
            ctripTasks: ctripTasks,
            meituanTasks: meituanTasks,
          )
        : ctripTasks.isNotEmpty
        ? await automationService.startCtripBatch(ctripTasks)
        : await automationService.startMeituanBatch(meituanTasks);
    resumingPendingBatch = false;
    if (!mounted) return;
    if (started) {
      await beginBatchReportHistory(ctripTasks, meituanTasks);
      return;
    }
    setState(() => batchRunning = false);
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('截图采集启动失败，请重试')));
  }

  Future<void> startAllCollections() async {
    final ctripTasks = <Map<String, String>>[];
    final meituanTasks = <Map<String, String>>[];
    for (var projectIndex = 0; projectIndex < projects.length; projectIndex++) {
      final project = projects[projectIndex];
      if (project.ctripSelected) {
        for (final hotel in [project.mainHotel, ...project.competitors]) {
          if (!hotel.hasNameForPlatform('携程')) continue;
          ctripTasks.add({
            'taskId': '$projectIndex|${hotel.id}',
            'city': project.city,
            'hotelName': hotel.ctripName,
          });
        }
      }
      if (project.meituanSelected) {
        for (final hotel in [project.mainHotel, ...project.competitors]) {
          if (!hotel.hasNameForPlatform('美团')) continue;
          meituanTasks.add({
            'taskId': '$projectIndex|${hotel.id}',
            'city': project.city,
            'hotelName': hotel.meituanName,
          });
        }
      }
    }

    if (ctripTasks.isEmpty && meituanTasks.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('没有可执行的酒店采集任务')));
      return;
    }

    setState(() => batchRunning = true);
    if (!await automationService.isScreenCaptureReady()) {
      pendingCtripTasks = ctripTasks;
      pendingMeituanTasks = meituanTasks;
      final requested = await automationService.requestScreenCapture();
      if (requested) return;
      pendingCtripTasks = null;
      pendingMeituanTasks = null;
      if (!mounted) return;
      setState(() => batchRunning = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('无法请求截图采集所需的屏幕授权')));
      return;
    }
    final started = ctripTasks.isNotEmpty && meituanTasks.isNotEmpty
        ? await automationService.startCombinedBatch(
            ctripTasks: ctripTasks,
            meituanTasks: meituanTasks,
          )
        : ctripTasks.isNotEmpty
        ? await automationService.startCtripBatch(ctripTasks)
        : await automationService.startMeituanBatch(meituanTasks);
    if (!mounted) return;
    if (started) {
      await beginBatchReportHistory(ctripTasks, meituanTasks);
    } else {
      setState(() => batchRunning = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请完成权限授权，然后再次点击“全”')));
    }
  }

  Future<void> beginBatchReportHistory(
    List<Map<String, String>> ctripTasks,
    List<Map<String, String>> meituanTasks,
  ) async {
    final projectIndexes = <int>{};
    for (final task in [...ctripTasks, ...meituanTasks]) {
      final index = int.tryParse(task['taskId']?.split('|').first ?? '');
      if (index != null && index >= 0 && index < projects.length) {
        projectIndexes.add(index);
      }
    }
    for (final index in projectIndexes) {
      await ReportHistoryService.beginCollection(projects[index]);
    }
    await saveProjects();
    if (mounted) setState(() {});
  }

  Future<void> chooseCompletionSound() async {
    try {
      final selected = await automationService.chooseCompletionSound();
      if (!mounted || selected == null) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('采集完成铃声已更新')));
    } on PlatformException {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('无法选择铃声文件，请重试')));
    }
  }

  Future<void> openAllCurrentResults() async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => AllCurrentResultsPage(projects: projects),
      ),
    );
  }

  Future<void> confirmStartAllCollections() async {
    if (batchRunning) return;
    final confirmed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const ConfirmAllCollectionDialog(),
    );
    if (confirmed == true && mounted) await startAllCollections();
  }

  Future<void> syncBatchCaptures() async {
    final captures = await automationService.getBatchCtripCaptures();
    var changed = false;
    for (final capture in captures) {
      final parts = capture['taskId']?.toString().split('|') ?? const [];
      if (parts.length != 2) continue;
      final projectIndex = int.tryParse(parts[0]);
      if (projectIndex == null || projectIndex >= projects.length) continue;
      final project = projects[projectIndex];
      final hotels = [project.mainHotel, ...project.competitors];
      final hotel = hotels.where((item) => item.id == parts[1]).firstOrNull;
      if (hotel == null) continue;
      final capturedAt = (capture['capturedAt'] as num?)?.toInt() ?? 0;
      final history = project.ctripReports.putIfAbsent(
        hotel.id,
        HotelReportHistory.new,
      );
      if (history.previous != null &&
          history.previous!.hotelName.trim() != hotel.ctripName.trim()) {
        history.previous = null;
        changed = true;
      }
      if (history.current?.time.millisecondsSinceEpoch == capturedAt) continue;
      history.current = ReportData(
        time: DateTime.fromMillisecondsSinceEpoch(capturedAt),
        platform: '携程',
        hotelName: capture['hotelName']?.toString() ?? hotel.ctripName,
        roomNames: const [],
        lowestPrice: (capture['price'] as num?)?.toInt(),
        resultStatus: capture['status']?.toString(),
        screenshotPath: capture['screenshotPath']?.toString(),
      );
      changed = true;
    }
    final meituanCaptures = await automationService.getBatchMeituanCaptures();
    for (final capture in meituanCaptures) {
      final parts = capture['taskId']?.toString().split('|') ?? const [];
      if (parts.length != 2) continue;
      final projectIndex = int.tryParse(parts[0]);
      if (projectIndex == null || projectIndex >= projects.length) continue;
      final project = projects[projectIndex];
      final hotels = [project.mainHotel, ...project.competitors];
      final hotel = hotels.where((item) => item.id == parts[1]).firstOrNull;
      if (hotel == null) continue;
      final capturedAt = (capture['capturedAt'] as num?)?.toInt() ?? 0;
      final history = project.meituanReports.putIfAbsent(
        hotel.id,
        HotelReportHistory.new,
      );
      if (history.current?.time.millisecondsSinceEpoch == capturedAt) continue;
      history.current = ReportData(
        time: DateTime.fromMillisecondsSinceEpoch(capturedAt),
        platform: '美团',
        hotelName: capture['hotelName']?.toString() ?? hotel.meituanName,
        roomNames: const [],
        lowestPrice: (capture['price'] as num?)?.toInt(),
        resultStatus: capture['status']?.toString(),
        screenshotPath: capture['screenshotPath']?.toString(),
      );
      changed = true;
    }
    if (changed) await saveProjects();
    if (mounted) setState(() => batchRunning = false);
  }

  Future<void> loadProjects() async {
    setState(() {
      loading = true;
      loadError = null;
    });

    try {
      final loadedProjects = await widget.storage.loadProjects();
      final initialProjects =
          loadedProjects ??
          [
            MonitorProject(
              name: '上海酒店竞品监控',
              city: '上海',
              mainHotel: HotelConfig.fromLegacyName('主体酒店'),
              competitors: [
                HotelConfig.fromLegacyName('竞对酒店 A'),
                HotelConfig.fromLegacyName('竞对酒店 B'),
              ],
            ),
          ];

      var sanitizedReports = false;
      for (final project in initialProjects) {
        for (final hotel in [project.mainHotel, ...project.competitors]) {
          final history = project.ctripReports[hotel.id];
          if (history?.previous != null &&
              history!.previous!.hotelName.trim() != hotel.ctripName.trim()) {
            history.previous = null;
            sanitizedReports = true;
          }
        }
      }

      if (!mounted) return;
      setState(() {
        projects = initialProjects;
        loading = false;
      });

      if (loadedProjects == null || sanitizedReports) await saveProjects();
      await syncBatchCaptures();
      await refreshCaptchaPendingState();
    } catch (_) {
      if (!mounted) return;
      setState(() {
        loading = false;
        loadError = '本地项目数据读取失败。原数据不会被覆盖，请重试。';
      });
    }
  }

  Future<void> saveProjects() async {
    await widget.storage.saveProjects(projects);
  }

  Future<void> clearTodayViewCounts() async {
    setState(() {
      for (final project in projects) {
        project.todayViewCount = 0;
      }
    });

    try {
      await saveProjects();
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('所有项目的今日查看已归零')));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('清除失败，请重试')));
    }
  }

  void recordProjectView(MonitorProject project) {
    final now = DateTime.now();
    final lastDate = project.lastViewDate;

    final sameDay =
        lastDate != null &&
        lastDate.year == now.year &&
        lastDate.month == now.month &&
        lastDate.day == now.day;

    setState(() {
      project.todayViewCount = sameDay ? project.todayViewCount + 1 : 1;
      project.lastViewDate = now;
    });

    saveProjects();
  }

  Future<void> addProject() async {
    final result = await showProjectDialog(context);

    if (result != null) {
      setState(() {
        projects.add(result);
      });
      await saveProjects();
    }
  }

  Future<void> editProject(int index) async {
    final result = await showProjectDialog(context, project: projects[index]);

    if (result != null) {
      setState(() {
        projects[index] = result;
      });
      await saveProjects();
    }
  }

  Future<void> deleteProject(int index) async {
    final project = projects[index];

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('删除监控项目'),
          content: Text('确定删除“${project.name}”吗？'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('删除'),
            ),
          ],
        );
      },
    );

    if (confirmed == true) {
      setState(() {
        projects.removeAt(index);
      });
      await saveProjects();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (loadError != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('酒店监控')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, size: 64),
                const SizedBox(height: 16),
                Text(loadError!, textAlign: TextAlign.center),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: loadProjects,
                  icon: const Icon(Icons.refresh),
                  label: const Text('重新读取'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '酒店监控',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          IconButton(
            onPressed: addProject,
            tooltip: '新增监控项目',
            icon: const Icon(Icons.add),
          ),
        ],
      ),
      body: projects.isEmpty
          ? EmptyProjectView(onAdd: addProject)
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const AutomationStatusCard(),
                if (captchaPendingState != null) ...[
                  const SizedBox(height: 12),
                  CaptchaPendingCard(
                    state: captchaPendingState!,
                    resuming: captchaResumeStarting,
                    onResume: resumeCaptchaPendingTasks,
                    onDiscard: discardCaptchaPendingTasks,
                  ),
                ],
                const SizedBox(height: 16),
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        '我的监控项目',
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    TextButton.icon(
                      onPressed: clearTodayViewCounts,
                      icon: const Icon(Icons.cleaning_services_outlined),
                      label: const Text('清除'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  height: 54,
                  child: OutlinedButton.icon(
                    onPressed: openAllCurrentResults,
                    icon: const Icon(Icons.summarize_outlined),
                    label: const Text('总 · 查看全部结果'),
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  height: 54,
                  child: FilledButton.icon(
                    onPressed: batchRunning || captchaPendingState != null
                        ? null
                        : confirmStartAllCollections,
                    onLongPress: batchRunning || captchaPendingState != null
                        ? null
                        : chooseCompletionSound,
                    icon: batchRunning
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.notifications_active_outlined),
                    label: Text(batchRunning ? '正在采集全部项目…' : '全 · 采集所有项目'),
                  ),
                ),
                const SizedBox(height: 6),
                Center(
                  child: Text(
                    '点击后确认 · 长按可更换完成铃声',
                    style: TextStyle(color: Colors.grey.shade600, fontSize: 12),
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  '点击项目查看当次采集截图和上一次历史',
                  style: TextStyle(color: Colors.grey.shade600),
                ),
                const SizedBox(height: 16),
                ...projects.asMap().entries.map((entry) {
                  final index = entry.key;
                  final project = entry.value;

                  return ProjectCard(
                    project: project,
                    onTap: () async {
                      recordProjectView(project);

                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => MonitorPage(
                            project: project,
                            projectIndex: index,
                            onChanged: saveProjects,
                          ),
                        ),
                      );

                      setState(() {});
                    },
                    onEdit: () => editProject(index),
                    onDelete: () => deleteProject(index),
                  );
                }),
              ],
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: addProject,
        icon: const Icon(Icons.add),
        label: const Text('新增项目'),
      ),
    );
  }
}

class ConfirmAllCollectionDialog extends StatefulWidget {
  const ConfirmAllCollectionDialog({super.key});

  @override
  State<ConfirmAllCollectionDialog> createState() =>
      _ConfirmAllCollectionDialogState();
}

class _ConfirmAllCollectionDialogState
    extends State<ConfirmAllCollectionDialog> {
  Timer? timer;
  int secondsRemaining = 3;

  @override
  void initState() {
    super.initState();
    timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      if (secondsRemaining <= 1) {
        timer?.cancel();
        Navigator.pop(context, true);
        return;
      }
      setState(() => secondsRemaining -= 1);
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      icon: const Icon(Icons.notifications_active_outlined, size: 34),
      title: const Text('确认采集全部项目？'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('将依次打开携程和美团，采集所有监控项目的酒店截图。', textAlign: TextAlign.center),
          const SizedBox(height: 16),
          Text(
            '$secondsRemaining 秒后自动确认并开始',
            style: TextStyle(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          child: const Text('立即确认'),
        ),
      ],
    );
  }
}

class CaptchaPendingCard extends StatelessWidget {
  final Map<String, dynamic> state;
  final bool resuming;
  final VoidCallback onResume;
  final VoidCallback onDiscard;

  const CaptchaPendingCard({
    super.key,
    required this.state,
    required this.resuming,
    required this.onResume,
    required this.onDiscard,
  });

  @override
  Widget build(BuildContext context) {
    final platform = state['platform']?.toString() ?? '平台';
    final hotelName = state['hotelName']?.toString() ?? '';
    final pendingCount = (state['pendingCount'] as num?)?.toInt() ?? 0;
    final screenshotPath = state['screenshotPath']?.toString().trim() ?? '';
    final screenshotExists =
        screenshotPath.isNotEmpty && File(screenshotPath).existsSync();

    return Card(
      margin: EdgeInsets.zero,
      color: Colors.orange.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.verified_user_outlined,
                  color: Colors.orange.shade800,
                ),
                const SizedBox(width: 9),
                const Expanded(
                  child: Text(
                    '需要人工完成验证码',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text('$platform · $hotelName'),
            const SizedBox(height: 4),
            Text('已停止自动点击，并保留 $pendingCount 个未完成任务。'),
            const SizedBox(height: 4),
            Text(
              '请先在$platform中人工完成验证，再回到这里继续。',
              style: TextStyle(color: Colors.orange.shade900),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton.icon(
                  onPressed: resuming ? null : onResume,
                  icon: resuming
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.play_arrow),
                  label: Text(resuming ? '正在继续…' : '继续未完成任务'),
                ),
                if (screenshotExists)
                  OutlinedButton.icon(
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => ScreenshotViewerPage(
                            title: '$platform · 验证码现场',
                            imagePath: screenshotPath,
                          ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.image_outlined),
                    label: const Text('查看现场'),
                  ),
                TextButton(
                  onPressed: resuming ? null : onDiscard,
                  child: const Text('放弃剩余任务'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class EmptyProjectView extends StatelessWidget {
  final VoidCallback onAdd;

  const EmptyProjectView({super.key, required this.onAdd});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.hotel_outlined,
              size: 80,
              color: Colors.blueGrey.shade300,
            ),
            const SizedBox(height: 20),
            const Text(
              '还没有监控项目',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text('先新增一个主体酒店和竞对酒店吧'),
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: const Text('新增监控项目'),
            ),
          ],
        ),
      ),
    );
  }
}

class ProjectCard extends StatelessWidget {
  final MonitorProject project;
  final VoidCallback onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const ProjectCard({
    super.key,
    required this.project,
    required this.onTap,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      elevation: 0,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: Colors.blue.shade50,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(
                  Icons.apartment,
                  color: Colors.blue.shade700,
                  size: 28,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      project.name,
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '${project.city} · 主体：${project.mainHotel.displayName}',
                      style: TextStyle(color: Colors.grey.shade600),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '创建时间：${formatDateTime(project.createdAt)}',
                      style: TextStyle(
                        color: Colors.grey.shade600,
                        fontSize: 12,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '今日查看：${project.todayViewCount} 次',
                      style: TextStyle(
                        color: Colors.blueGrey.shade600,
                        fontSize: 12,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        PlatformTag(
                          text: '美团',
                          selected: project.meituanSelected,
                        ),
                        const SizedBox(width: 6),
                        PlatformTag(
                          text: '携程',
                          selected: project.ctripSelected,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              PopupMenuButton<String>(
                onSelected: (value) {
                  if (value == 'edit') {
                    onEdit();
                  }
                  if (value == 'delete') {
                    onDelete();
                  }
                },
                itemBuilder: (_) => const [
                  PopupMenuItem(value: 'edit', child: Text('编辑项目')),
                  PopupMenuItem(value: 'delete', child: Text('删除项目')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class PlatformTag extends StatelessWidget {
  final String text;
  final bool selected;

  const PlatformTag({super.key, required this.text, required this.selected});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: selected ? Colors.green.shade50 : Colors.grey.shade100,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        selected ? text : '$text未选',
        style: TextStyle(
          fontSize: 12,
          color: selected ? Colors.green.shade700 : Colors.grey.shade600,
        ),
      ),
    );
  }
}

class AutomationStatusCard extends StatefulWidget {
  const AutomationStatusCard({super.key});

  @override
  State<AutomationStatusCard> createState() => _AutomationStatusCardState();
}

class _AutomationStatusCardState extends State<AutomationStatusCard>
    with WidgetsBindingObserver {
  static const automationService = AndroidAutomationService();
  bool accessibilityEnabled = false;
  bool checking = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    checkAccessibility();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) checkAccessibility();
  }

  Future<void> checkAccessibility() async {
    final enabled = await automationService.isAccessibilityEnabled();
    if (!mounted) return;
    setState(() {
      accessibilityEnabled = enabled;
      checking = false;
    });
  }

  Future<void> openCtrip() async {
    try {
      final opened = await automationService.openCtrip();
      if (!mounted || opened) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('没有找到携程 APP')));
    } on PlatformException {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('打开携程失败')));
    }
  }

  Future<void> openCtripHotel() async {
    try {
      final started = await automationService.openCtripHotel();
      if (!mounted || started) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请先开启无障碍权限并确认携程已安装')));
    } on PlatformException {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('自动进入酒店测试启动失败')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final statusText = checking
        ? '正在检查权限…'
        : accessibilityEnabled
        ? '无障碍权限已开启'
        : '需要开启无障碍权限';

    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  accessibilityEnabled
                      ? Icons.check_circle
                      : Icons.settings_accessibility,
                  color: accessibilityEnabled ? Colors.green : Colors.orange,
                ),
                const SizedBox(width: 10),
                const Expanded(
                  child: Text(
                    '手机自动化',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(statusText),
            const SizedBox(height: 12),
            Wrap(
              spacing: 10,
              runSpacing: 8,
              children: [
                if (!accessibilityEnabled)
                  FilledButton.icon(
                    onPressed: automationService.openAccessibilitySettings,
                    icon: const Icon(Icons.settings),
                    label: const Text('开启无障碍权限'),
                  ),
                OutlinedButton.icon(
                  onPressed: openCtrip,
                  icon: const Icon(Icons.open_in_new),
                  label: const Text('打开携程测试'),
                ),
                FilledButton.icon(
                  onPressed: accessibilityEnabled ? openCtripHotel : null,
                  icon: const Icon(Icons.hotel),
                  label: const Text('自动进入酒店'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
