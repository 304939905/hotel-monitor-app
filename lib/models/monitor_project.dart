class ReportData {
  final DateTime time;
  final String platform;
  final String hotelName;
  final List<String> roomNames;
  final int? lowestPrice;
  final String? resultStatus;
  final String? screenshotPath;

  const ReportData({
    required this.time,
    required this.platform,
    required this.hotelName,
    required this.roomNames,
    this.lowestPrice,
    this.resultStatus,
    this.screenshotPath,
  });

  String get effectiveStatus =>
      resultStatus ?? (lowestPrice == null ? 'error' : 'success');

  Map<String, dynamic> toJson() => {
    'time': time.toIso8601String(),
    'platform': platform,
    'hotelName': hotelName,
    'roomNames': roomNames,
    'lowestPrice': lowestPrice,
    'resultStatus': resultStatus,
    'screenshotPath': screenshotPath,
  };

  factory ReportData.fromJson(Map<String, dynamic> json) {
    return ReportData(
      time: DateTime.parse(json['time'] as String),
      platform: json['platform'] as String? ?? '',
      hotelName: json['hotelName'] as String? ?? '',
      roomNames: List<String>.from(json['roomNames'] as List? ?? const []),
      lowestPrice: (json['lowestPrice'] as num?)?.toInt(),
      resultStatus: json['resultStatus'] as String?,
      screenshotPath: json['screenshotPath'] as String?,
    );
  }
}

class HotelReportHistory {
  ReportData? current;
  ReportData? previous;

  HotelReportHistory({this.current, this.previous});

  Map<String, dynamic> toJson() => {
    'current': current?.toJson(),
    'previous': previous?.toJson(),
  };

  factory HotelReportHistory.fromJson(Map<String, dynamic> json) {
    return HotelReportHistory(
      current: json['current'] == null
          ? null
          : ReportData.fromJson(
              Map<String, dynamic>.from(json['current'] as Map),
            ),
      previous: json['previous'] == null
          ? null
          : ReportData.fromJson(
              Map<String, dynamic>.from(json['previous'] as Map),
            ),
    );
  }
}

class HotelConfig {
  final String id;
  String meituanName;
  String ctripName;

  HotelConfig({
    required this.id,
    required this.meituanName,
    required this.ctripName,
  });

  factory HotelConfig.create({
    required String meituanName,
    required String ctripName,
  }) {
    return HotelConfig(
      id: 'hotel_${DateTime.now().microsecondsSinceEpoch}',
      meituanName: meituanName,
      ctripName: ctripName,
    );
  }

  factory HotelConfig.fromLegacyName(String name) {
    return HotelConfig(id: name, meituanName: name, ctripName: name);
  }

  String get displayName {
    if (meituanName.isEmpty) return ctripName;
    if (ctripName.isEmpty || meituanName == ctripName) return meituanName;
    return '$meituanName / $ctripName';
  }

  bool hasNameForPlatform(String platform) =>
      nameForPlatform(platform).trim().isNotEmpty;

  String nameForPlatform(String platform) {
    return platform == '美团' ? meituanName : ctripName;
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'meituanName': meituanName,
    'ctripName': ctripName,
  };

  factory HotelConfig.fromJson(Map<String, dynamic> json) {
    final meituanName = json['meituanName'] as String? ?? '';
    final ctripName = json['ctripName'] as String? ?? meituanName;
    return HotelConfig(
      id: json['id'] as String? ?? meituanName,
      meituanName: meituanName,
      ctripName: ctripName,
    );
  }
}

class MonitorProject {
  String name;
  String city;
  HotelConfig mainHotel;
  List<HotelConfig> competitors;
  bool meituanSelected;
  bool ctripSelected;
  DateTime createdAt;
  int todayViewCount;
  DateTime? lastViewDate;
  final Map<String, HotelReportHistory> meituanReports;
  final Map<String, HotelReportHistory> ctripReports;

  MonitorProject({
    required this.name,
    required this.city,
    required this.mainHotel,
    required this.competitors,
    this.meituanSelected = true,
    this.ctripSelected = true,
    DateTime? createdAt,
    this.todayViewCount = 0,
    this.lastViewDate,
    Map<String, HotelReportHistory>? meituanReports,
    Map<String, HotelReportHistory>? ctripReports,
  }) : meituanReports = meituanReports ?? {},
       ctripReports = ctripReports ?? {},
       createdAt = createdAt ?? DateTime.now();

  Map<String, dynamic> toJson() => {
    'name': name,
    'city': city,
    'mainHotelConfig': mainHotel.toJson(),
    'competitorConfigs': competitors.map((hotel) => hotel.toJson()).toList(),
    'meituanSelected': meituanSelected,
    'ctripSelected': ctripSelected,
    'createdAt': createdAt.toIso8601String(),
    'todayViewCount': todayViewCount,
    'lastViewDate': lastViewDate?.toIso8601String(),
    'meituanReports': meituanReports.map(
      (hotel, history) => MapEntry(hotel, history.toJson()),
    ),
    'ctripReports': ctripReports.map(
      (hotel, history) => MapEntry(hotel, history.toJson()),
    ),
  };

  factory MonitorProject.fromJson(Map<String, dynamic> json) {
    Map<String, HotelReportHistory> decodeReports(dynamic value) {
      if (value is! Map) return {};

      final result = <String, HotelReportHistory>{};
      for (final entry in value.entries) {
        if (entry.value is Map) {
          result[entry.key.toString()] = HotelReportHistory.fromJson(
            Map<String, dynamic>.from(entry.value as Map),
          );
        }
      }
      return result;
    }

    HotelConfig decodeMainHotel() {
      final value = json['mainHotelConfig'];
      if (value is Map) {
        return HotelConfig.fromJson(Map<String, dynamic>.from(value));
      }
      return HotelConfig.fromLegacyName(json['mainHotel'] as String? ?? '');
    }

    List<HotelConfig> decodeCompetitors() {
      final value = json['competitorConfigs'];
      if (value is List) {
        return value
            .whereType<Map>()
            .map(
              (item) => HotelConfig.fromJson(Map<String, dynamic>.from(item)),
            )
            .toList();
      }
      return List<String>.from(
        json['competitors'] as List? ?? const [],
      ).map(HotelConfig.fromLegacyName).toList();
    }

    final mainHotel = decodeMainHotel();
    final competitors = decodeCompetitors();
    final meituanReports = decodeReports(json['meituanReports']);
    final ctripReports = decodeReports(json['ctripReports']);

    void migrateReportKeys(Map<String, HotelReportHistory> reports) {
      for (final hotel in [mainHotel, ...competitors]) {
        if (!reports.containsKey(hotel.id)) {
          final legacyReport =
              reports.remove(hotel.meituanName) ??
              reports.remove(hotel.ctripName);
          if (legacyReport != null) reports[hotel.id] = legacyReport;
        }
      }
    }

    migrateReportKeys(meituanReports);
    migrateReportKeys(ctripReports);

    return MonitorProject(
      name: json['name'] as String? ?? '',
      city: json['city'] as String? ?? '',
      mainHotel: mainHotel,
      competitors: competitors,
      meituanSelected: json['meituanSelected'] as bool? ?? true,
      ctripSelected: json['ctripSelected'] as bool? ?? true,
      createdAt: json['createdAt'] == null
          ? null
          : DateTime.tryParse(json['createdAt'] as String),
      todayViewCount: json['todayViewCount'] as int? ?? 0,
      lastViewDate: json['lastViewDate'] == null
          ? null
          : DateTime.tryParse(json['lastViewDate'] as String),
      meituanReports: meituanReports,
      ctripReports: ctripReports,
    );
  }
}
