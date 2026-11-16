part of '../main.dart';

class MonitorPage extends StatefulWidget {
  final MonitorProject project;
  final int projectIndex;
  final Future<void> Function() onChanged;
  final CollectionService collectionService;

  const MonitorPage({
    super.key,
    required this.project,
    this.projectIndex = -1,
    required this.onChanged,
    this.collectionService = const MockCollectionService(),
  });

  @override
  State<MonitorPage> createState() => _MonitorPageState();
}

class _MonitorPageState extends State<MonitorPage> with WidgetsBindingObserver {
  static const automationService = AndroidAutomationService();
  bool collecting = false;
  bool resumeCollectionAfterCapturePermission = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    syncCtripCapture();
    syncMeituanCapture();
    refreshCollectionState();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      syncCtripCapture();
      syncMeituanCapture();
      refreshCollectionState();
      if (resumeCollectionAfterCapturePermission) {
        resumeCollectionAfterCapturePermission = false;
        Future<void>.delayed(
          const Duration(milliseconds: 700),
          startCollection,
        );
      }
    }
  }

  Future<void> syncCtripCapture() async {
    final captures = await automationService.getBatchCtripCaptures();
    final lastCapture = await automationService.getLastCtripCapture();
    if (lastCapture != null) captures.add(lastCapture);
    var changed = false;
    final hasProjectResult = captures.any(
      (capture) => ctripCaptureBelongsToProject(capture),
    );
    for (final capture in captures) {
      changed = applyCtripCapture(capture) || changed;
    }
    if (changed) await widget.onChanged();
    if (mounted && (changed || hasProjectResult)) setState(() {});
  }

  bool ctripCaptureBelongsToProject(Map<String, dynamic> capture) {
    final parts = capture['taskId']?.toString().split('|') ?? const [];
    if (parts.length == 2 && parts[1].isNotEmpty) {
      return allHotels.any((hotel) => hotel.id == parts[1]);
    }
    final name = capture['hotelName']?.toString().trim() ?? '';
    return allHotels.any((hotel) => hotel.ctripName.trim() == name);
  }

  bool applyCtripCapture(Map<String, dynamic> capture) {
    final capturedAt = (capture['capturedAt'] as num?)?.toInt() ?? 0;
    if (capturedAt <= 0) return false;
    final taskId = capture['taskId']?.toString() ?? '';
    final taskParts = taskId.split('|');
    final hotels = allHotels;
    HotelConfig? hotel;

    // Batch results carry the exact hotel id. Never let the last batch result
    // fall back to the main hotel, otherwise a competitor can overwrite it.
    if (taskParts.length == 2 && taskParts[1].isNotEmpty) {
      hotel = hotels.where((item) => item.id == taskParts[1]).firstOrNull;
      if (hotel == null) return false;
    } else {
      final capturedName = capture['hotelName']?.toString().trim() ?? '';
      hotel = hotels
          .where((item) => item.ctripName.trim() == capturedName)
          .firstOrNull;
      if (hotel == null) return false;
    }

    final history = widget.project.ctripReports.putIfAbsent(
      hotel.id,
      HotelReportHistory.new,
    );
    if ((history.current?.time.millisecondsSinceEpoch ?? 0) >= capturedAt) {
      return false;
    }
    history.current = ReportData(
      time: DateTime.fromMillisecondsSinceEpoch(capturedAt),
      platform: '携程',
      hotelName: capture['hotelName']?.toString() ?? hotel.ctripName,
      roomNames: const [],
      lowestPrice: (capture['price'] as num?)?.toInt(),
      resultStatus: capture['status']?.toString(),
      screenshotPath: capture['screenshotPath']?.toString(),
    );
    return true;
  }

  Future<void> syncMeituanCapture() async {
    final captures = await automationService.getBatchMeituanCaptures();
    final lastCapture = await automationService.getLastMeituanCapture();
    if (lastCapture != null) captures.add(lastCapture);
    var changed = false;
    final hasProjectResult = captures.any(
      (capture) => meituanCaptureBelongsToProject(capture),
    );
    for (final capture in captures) {
      changed = applyMeituanCapture(capture) || changed;
    }
    if (changed) await widget.onChanged();
    if (mounted && (changed || hasProjectResult)) setState(() {});
  }

  bool meituanCaptureBelongsToProject(Map<String, dynamic> capture) {
    final parts = capture['taskId']?.toString().split('|') ?? const [];
    if (parts.length == 2 && parts[1].isNotEmpty) {
      return allHotels.any((hotel) => hotel.id == parts[1]);
    }
    final name = capture['hotelName']?.toString().trim() ?? '';
    return allHotels.any((hotel) => hotel.meituanName.trim() == name);
  }

  Future<void> refreshCollectionState() async {
    final running = await automationService.isAutomationRunning();
    if (!mounted || running || !collecting) return;
    setState(() => collecting = false);
  }

  bool applyMeituanCapture(Map<String, dynamic> capture) {
    final capturedAt = (capture['capturedAt'] as num?)?.toInt() ?? 0;
    if (capturedAt <= 0) return false;
    final taskId = capture['taskId']?.toString() ?? '';
    final taskParts = taskId.split('|');
    final capturedName = capture['hotelName']?.toString().trim() ?? '';
    final HotelConfig? hotel = taskParts.length == 2 && taskParts[1].isNotEmpty
        ? allHotels.where((item) => item.id == taskParts[1]).firstOrNull
        : allHotels
              .where((item) => item.meituanName.trim() == capturedName)
              .firstOrNull;
    if (hotel == null) return false;
    final history = widget.project.meituanReports.putIfAbsent(
      hotel.id,
      HotelReportHistory.new,
    );
    if ((history.current?.time.millisecondsSinceEpoch ?? 0) >= capturedAt) {
      return false;
    }
    history.current = ReportData(
      time: DateTime.fromMillisecondsSinceEpoch(capturedAt),
      platform: '美团',
      hotelName: capturedName,
      roomNames: const [],
      lowestPrice: (capture['price'] as num?)?.toInt(),
      resultStatus: capture['status']?.toString(),
      screenshotPath: capture['screenshotPath']?.toString(),
    );
    return true;
  }

  List<HotelConfig> get allHotels => [
    widget.project.mainHotel,
    ...widget.project.competitors,
  ];

  List<HotelConfig> hotelsForPlatform(String platform) =>
      allHotels.where((hotel) => hotel.hasNameForPlatform(platform)).toList();

  Future<void> startCollection() async {
    final project = widget.project;

    final accessibilityConnected = await automationService
        .isAccessibilityConnected();
    if (!mounted) return;
    if (!accessibilityConnected) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('自动化服务未连接，请在无障碍设置中关闭后重新开启')));
      return;
    }

    final taskPrefix = widget.projectIndex >= 0
        ? widget.projectIndex.toString()
        : 'single';
    final ctripTasks = <Map<String, String>>[];
    final meituanTasks = <Map<String, String>>[];
    for (final hotel in allHotels) {
      if (project.ctripSelected && hotel.hasNameForPlatform('携程')) {
        ctripTasks.add({
          'taskId': '$taskPrefix|${hotel.id}',
          'city': project.city,
          'hotelName': hotel.ctripName,
        });
      }
      if (project.meituanSelected && hotel.hasNameForPlatform('美团')) {
        meituanTasks.add({
          'taskId': '$taskPrefix|${hotel.id}',
          'city': project.city,
          'hotelName': hotel.meituanName,
        });
      }
    }

    if (ctripTasks.isEmpty && meituanTasks.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('没有可执行的酒店截图任务')));
      return;
    }

    if (!await automationService.isScreenCaptureReady()) {
      resumeCollectionAfterCapturePermission = true;
      await automationService.requestScreenCapture();
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请允许屏幕截图；授权后会自动开始')));
      return;
    }

    setState(() {
      collecting = true;
    });

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
      await ReportHistoryService.beginCollection(project);
      await widget.onChanged();
      if (mounted) setState(() {});
    } else {
      setState(() => collecting = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('截图采集启动失败，请重试')));
    }
  }

  Future<void> testCtripForm() async {
    if (!widget.project.mainHotel.hasNameForPlatform('携程')) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('主体酒店没有填写携程名称')));
      return;
    }
    try {
      final started = await automationService.prepareCtripForm(
        city: widget.project.city,
        hotelName: widget.project.mainHotel.ctripName,
      );
      if (!mounted || started) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请确认无障碍权限已开启且携程已经安装')));
    } on PlatformException {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('携程最低起价测试启动失败')));
    }
  }

  Widget buildPlatformSection({
    required String platform,
    required Map<String, HotelReportHistory> reports,
    required bool selected,
  }) {
    if (!selected) {
      return const SizedBox.shrink();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          platform,
          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 10),
        ...hotelsForPlatform(platform).map((hotel) {
          final history = reports[hotel.id];

          return HotelReportSection(
            platform: platform,
            hotelName: hotel.nameForPlatform(platform),
            current: history?.current,
          );
        }),
        const SizedBox(height: 8),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final project = widget.project;

    return Scaffold(
      appBar: AppBar(title: Text(project.name)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 30),
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '页面截图采集',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  '自动确认目标酒店，并保存包含价格的真实页面截图',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                ),
                const SizedBox(height: 12),
                Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 4,
                  runSpacing: 8,
                  children: [
                    FilterChip(
                      label: const Text('美团'),
                      selected: project.meituanSelected,
                      onSelected: (value) {
                        setState(() {
                          project.meituanSelected = value;
                        });
                        widget.onChanged();
                      },
                    ),
                    FilterChip(
                      label: const Text('携程'),
                      selected: project.ctripSelected,
                      onSelected: (value) {
                        setState(() {
                          project.ctripSelected = value;
                        });
                        widget.onChanged();
                      },
                    ),
                    const SizedBox(width: 6),
                    FilledButton.icon(
                      onPressed: collecting ? null : startCollection,
                      icon: collecting
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.manage_search_outlined),
                      label: Text(collecting ? '采集中...' : '采集最低起价'),
                    ),
                    OutlinedButton.icon(
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                PreviousCollectionPage(project: project),
                          ),
                        );
                      },
                      icon: const Icon(Icons.history),
                      label: const Text('历史'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            '当次采集截图',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          buildPlatformSection(
            platform: '美团',
            reports: project.meituanReports,
            selected: project.meituanSelected,
          ),
          buildPlatformSection(
            platform: '携程',
            reports: project.ctripReports,
            selected: project.ctripSelected,
          ),
          const SizedBox(height: 8),
          Text(
            '酒店数量：${allHotels.length} 家（主体酒店 1 家，竞对酒店 ${project.competitors.length} 家）',
            style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
          ),
        ],
      ),
    );
  }
}

class HotelReportSection extends StatelessWidget {
  final String platform;
  final String hotelName;
  final ReportData? current;

  const HotelReportSection({
    super.key,
    required this.platform,
    required this.hotelName,
    required this.current,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.hotel,
                  size: 19,
                  color: platform == '美团'
                      ? const Color(0xFFE85D04)
                      : const Color(0xFF1769D2),
                ),
                const SizedBox(width: 7),
                Expanded(
                  child: Text(
                    hotelName,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (current == null)
              const EmptyReportCard()
            else
              ReportCard(report: current!, isPrevious: false),
          ],
        ),
      ),
    );
  }
}

class ReportCard extends StatelessWidget {
  final ReportData report;
  final bool isPrevious;

  const ReportCard({super.key, required this.report, required this.isPrevious});

  @override
  Widget build(BuildContext context) {
    final status = report.effectiveStatus;
    final path = report.screenshotPath?.trim() ?? '';
    final screenshot = path.isNotEmpty ? File(path) : null;
    final screenshotExists = screenshot?.existsSync() ?? false;
    final statusText = switch (status) {
      'full' => '状态：满房',
      'review' => '状态：待人工核验',
      'update_blocked' => '状态：更新弹窗阻挡',
      'verification_required' => '状态：需要人工验证',
      'error' => '状态：采集错误',
      _ when screenshotExists => '状态：截图保存成功',
      _ => '状态：没有可用截图',
    };
    final statusColor = switch (status) {
      'full' => Colors.orange,
      'review' => Colors.amber.shade800,
      'update_blocked' => Colors.deepOrange,
      'verification_required' => Colors.deepOrange,
      'error' => Colors.red,
      _ when !screenshotExists => Colors.blueGrey,
      _ => Colors.green,
    };
    return Card(
      margin: EdgeInsets.zero,
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.access_time, size: 17, color: Colors.blueGrey),
                const SizedBox(width: 6),
                Text(
                  isPrevious ? '上一次汇报' : '当前汇报',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                Text(
                  formatDateTime(report.time),
                  style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Text('酒店：${report.hotelName}'),
            const SizedBox(height: 4),
            Text('平台：${report.platform}'),
            const SizedBox(height: 8),
            Text(
              statusText,
              style: TextStyle(color: statusColor, fontWeight: FontWeight.w600),
            ),
            if (status == 'review') ...[
              const SizedBox(height: 4),
              const Text(
                '搜索结果受遮挡或详情核验失败，请直接查看截图人工确认。',
                style: TextStyle(fontSize: 12, color: Colors.deepOrange),
              ),
            ],
            if (status == 'update_blocked') ...[
              const SizedBox(height: 4),
              const Text(
                '平台更新弹窗无法安全关闭，本次采集已停止，请人工处理后重试。',
                style: TextStyle(fontSize: 12, color: Colors.deepOrange),
              ),
            ],
            if (status == 'verification_required') ...[
              const SizedBox(height: 4),
              const Text(
                '平台弹出了安全验证码，自动化已经停止；人工验证后可从主页继续未完成任务。',
                style: TextStyle(fontSize: 12, color: Colors.deepOrange),
              ),
            ],
            const SizedBox(height: 10),
            if (screenshotExists)
              InkWell(
                onTap: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => ScreenshotViewerPage(
                        title: '${report.platform} · ${report.hotelName}',
                        imagePath: path,
                      ),
                    ),
                  );
                },
                borderRadius: BorderRadius.circular(12),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Container(
                    width: double.infinity,
                    height: 420,
                    color: Colors.black,
                    child: Image.file(
                      screenshot!,
                      fit: BoxFit.contain,
                      errorBuilder: (_, _, _) => const Center(
                        child: Text(
                          '截图文件无法读取',
                          style: TextStyle(color: Colors.white),
                        ),
                      ),
                    ),
                  ),
                ),
              )
            else
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  status == 'full'
                      ? '该酒店本次显示满房，未生成价格截图。'
                      : status == 'review'
                      ? '未能保存待核验的搜索结果页截图。'
                      : status == 'update_blocked'
                      ? '未能保存更新弹窗的现场截图。'
                      : status == 'verification_required'
                      ? '未能保存验证码的现场截图。'
                      : status == 'error'
                      ? '本次未能保存有效页面截图。'
                      : '这是截图版启用前的旧结果，没有对应图片。',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.grey.shade700),
                ),
              ),
            if (screenshotExists) ...[
              const SizedBox(height: 8),
              const Center(
                child: Text(
                  '点击截图可放大查看',
                  style: TextStyle(fontSize: 12, color: Colors.blueGrey),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class PreviousCollectionPage extends StatelessWidget {
  final MonitorProject project;

  const PreviousCollectionPage({super.key, required this.project});

  List<ReportData> get reports {
    final result = <ReportData>[];
    for (final hotel in [project.mainHotel, ...project.competitors]) {
      final meituan = project.meituanReports[hotel.id]?.previous;
      final ctrip = project.ctripReports[hotel.id]?.previous;
      if (_hasScreenshot(meituan)) result.add(meituan!);
      if (_hasScreenshot(ctrip)) result.add(ctrip!);
    }
    return result;
  }

  bool _hasScreenshot(ReportData? report) {
    final path = report?.screenshotPath?.trim() ?? '';
    return path.isNotEmpty && File(path).existsSync();
  }

  @override
  Widget build(BuildContext context) {
    final previousReports = reports;
    return Scaffold(
      appBar: AppBar(title: const Text('上次采集结果')),
      body: previousReports.isEmpty
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(28),
                child: Text('还没有上一次采集结果。完成两次采集后，这里会汇总展示上一次的所有截图。'),
              ),
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
              itemCount: previousReports.length,
              separatorBuilder: (_, _) => const SizedBox(height: 14),
              itemBuilder: (context, index) =>
                  ReportCard(report: previousReports[index], isPrevious: true),
            ),
    );
  }
}

class ScreenshotViewerPage extends StatelessWidget {
  final String title;
  final String imagePath;

  const ScreenshotViewerPage({
    super.key,
    required this.title,
    required this.imagePath,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text(title),
        foregroundColor: Colors.white,
        backgroundColor: Colors.black,
      ),
      body: Center(
        child: InteractiveViewer(
          minScale: 0.8,
          maxScale: 5,
          child: Image.file(
            File(imagePath),
            fit: BoxFit.contain,
            errorBuilder: (_, _, _) =>
                const Text('截图文件无法读取', style: TextStyle(color: Colors.white)),
          ),
        ),
      ),
    );
  }
}

class MockScreenshot extends StatelessWidget {
  final String platform;
  final int index;
  final String roomName;
  final String price;

  const MockScreenshot({
    super.key,
    required this.platform,
    required this.index,
    required this.roomName,
    required this.price,
  });

  @override
  Widget build(BuildContext context) {
    final isMeituan = platform == '美团';

    return Container(
      height: 142,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: isMeituan ? const Color(0xFFFFD1BD) : const Color(0xFFC9DEFF),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 5,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 25,
                height: 25,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: isMeituan
                      ? const Color(0xFFFF6B35)
                      : const Color(0xFF3478F6),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '$index',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  roomName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 12,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Container(
            height: 34,
            decoration: BoxDecoration(
              color: Colors.grey.shade100,
              borderRadius: BorderRadius.circular(6),
            ),
            child: const Center(
              child: Text(
                '不含早餐 · 普通公开价',
                style: TextStyle(fontSize: 10, color: Colors.black54),
              ),
            ),
          ),
          const Spacer(),
          Align(
            alignment: Alignment.centerRight,
            child: Text(
              price,
              style: TextStyle(
                color: isMeituan
                    ? const Color(0xFFE85D04)
                    : const Color(0xFF1769D2),
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class EmptyReportCard extends StatelessWidget {
  const EmptyReportCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Icon(Icons.info_outline, color: Colors.blueGrey.shade400),
            const SizedBox(width: 10),
            const Expanded(child: Text('还没有当次截图，请点击上方“采集最低起价”')),
          ],
        ),
      ),
    );
  }
}
