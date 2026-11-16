import '../models/monitor_project.dart';

abstract class CollectionService {
  const CollectionService();

  Future<void> collect(MonitorProject project);
}

/// Temporary implementation used until Android accessibility automation is
/// connected. It produces predictable report data without opening other apps.
class MockCollectionService extends CollectionService {
  final Duration delay;

  const MockCollectionService({this.delay = const Duration(seconds: 2)});

  @override
  Future<void> collect(MonitorProject project) async {
    if (delay > Duration.zero) await Future<void>.delayed(delay);

    final hotels = [project.mainHotel, ...project.competitors];
    final now = DateTime.now();

    if (project.meituanSelected) {
      _updateReports(
        reports: project.meituanReports,
        hotels: hotels,
        time: now,
        platform: '美团',
        roomNames: const ['舒适大床房', '高级大床房', '豪华双床房', '商务套房'],
      );
    }

    if (project.ctripSelected) {
      _updateReports(
        reports: project.ctripReports,
        hotels: hotels,
        time: now,
        platform: '携程',
        roomNames: const ['精选大床房', '高级双床房', '豪华大床房', '行政套房'],
      );
    }
  }

  void _updateReports({
    required Map<String, HotelReportHistory> reports,
    required List<HotelConfig> hotels,
    required DateTime time,
    required String platform,
    required List<String> roomNames,
  }) {
    for (final hotel in hotels) {
      if (!hotel.hasNameForPlatform(platform)) continue;
      final history = reports.putIfAbsent(hotel.id, HotelReportHistory.new);
      history.previous = history.current;
      history.current = ReportData(
        time: time,
        platform: platform,
        hotelName: hotel.nameForPlatform(platform),
        roomNames: roomNames,
      );
    }
  }
}
