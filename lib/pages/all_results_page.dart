part of '../main.dart';

class AllCurrentResultsPage extends StatelessWidget {
  final List<MonitorProject> projects;

  const AllCurrentResultsPage({super.key, required this.projects});

  List<ReportData> _currentReports(MonitorProject project) {
    final reports = <ReportData>[];
    for (final hotel in [project.mainHotel, ...project.competitors]) {
      final ctrip = project.ctripReports[hotel.id]?.current;
      final meituan = project.meituanReports[hotel.id]?.current;
      if (ctrip != null) reports.add(ctrip);
      if (meituan != null) reports.add(meituan);
    }
    return reports;
  }

  @override
  Widget build(BuildContext context) {
    final reportCount = projects.fold<int>(
      0,
      (count, project) => count + _currentReports(project).length,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('全部项目采集结果')),
      body: projects.isEmpty
          ? const Center(child: Text('还没有监控项目。'))
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
              children: [
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primaryContainer,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.summarize_outlined, size: 30),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              '当前采集结果汇总',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text('${projects.length} 个项目 · $reportCount 条结果'),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                ...projects.map((project) {
                  final reports = _currentReports(project);
                  return Container(
                    margin: const EdgeInsets.only(bottom: 18),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: Colors.blueGrey.shade100),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(
                              Icons.apartment,
                              color: Theme.of(context).colorScheme.primary,
                            ),
                            const SizedBox(width: 9),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    project.name,
                                    style: const TextStyle(
                                      fontSize: 18,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${project.city} · ${reports.length} 条结果',
                                    style: TextStyle(
                                      color: Colors.grey.shade600,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        if (reports.isEmpty)
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.all(18),
                            decoration: BoxDecoration(
                              color: Colors.grey.shade100,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Text(
                              '暂无当次采集结果',
                              textAlign: TextAlign.center,
                            ),
                          )
                        else
                          ...reports.map(
                            (report) => Padding(
                              padding: const EdgeInsets.only(bottom: 12),
                              child: ReportCard(
                                report: report,
                                isPrevious: false,
                              ),
                            ),
                          ),
                      ],
                    ),
                  );
                }),
              ],
            ),
    );
  }
}
