import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'models/monitor_project.dart';
import 'services/android_automation_service.dart';
import 'services/batch_hotel_parser.dart';
import 'services/collection_service.dart';
import 'services/project_storage.dart';
import 'services/report_history_service.dart';

part 'pages/home_page.dart';
part 'pages/all_results_page.dart';
part 'pages/monitor_page.dart';
part 'widgets/project_dialogs.dart';

void main() {
  runApp(const HotelMonitorApp());
}

class HotelMonitorApp extends StatelessWidget {
  const HotelMonitorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '酒店监控',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176B87)),
        scaffoldBackgroundColor: const Color(0xFFF4F7F9),
      ),
      home: const HomePage(),
    );
  }
}
