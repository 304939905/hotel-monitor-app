import 'package:flutter/services.dart';

class AndroidAutomationService {
  static const MethodChannel _channel = MethodChannel(
    'hotel_monitor/android_automation',
  );

  const AndroidAutomationService();

  Future<bool> isAccessibilityEnabled() async {
    try {
      return await _channel.invokeMethod<bool>('isAccessibilityEnabled') ??
          false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<bool> isAccessibilityConnected() async {
    return await _channel.invokeMethod<bool>('isAccessibilityConnected') ??
        false;
  }

  Future<bool> isAutomationRunning() async {
    return await _channel.invokeMethod<bool>('isAutomationRunning') ?? false;
  }

  Future<Map<String, dynamic>?> getCaptchaPendingState() async {
    try {
      final result = await _channel.invokeMapMethod<dynamic, dynamic>(
        'getCaptchaPendingState',
      );
      if (result == null || result.isEmpty) return null;
      return Map<String, dynamic>.from(result);
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  Future<bool> resumeCaptchaPending() async {
    return await _channel.invokeMethod<bool>('resumeCaptchaPending') ?? false;
  }

  Future<bool> clearCaptchaPending() async {
    return await _channel.invokeMethod<bool>('clearCaptchaPending') ?? false;
  }

  Future<void> openAccessibilitySettings() async {
    await _channel.invokeMethod<void>('openAccessibilitySettings');
  }

  Future<bool> isScreenCaptureReady() async {
    return await _channel.invokeMethod<bool>('isScreenCaptureReady') ?? false;
  }

  Future<bool> requestScreenCapture() async {
    return await _channel.invokeMethod<bool>('requestScreenCapture') ?? false;
  }

  Future<String?> chooseCompletionSound() async {
    return _channel.invokeMethod<String>('chooseCompletionSound');
  }

  Future<bool> openCtrip() async {
    return await _channel.invokeMethod<bool>('openCtrip') ?? false;
  }

  Future<bool> openCtripHotel() async {
    return await _channel.invokeMethod<bool>('openCtripHotel') ?? false;
  }

  Future<bool> prepareCtripForm({
    required String city,
    required String hotelName,
  }) async {
    return await _channel.invokeMethod<bool>('prepareCtripForm', {
          'city': city,
          'hotelName': hotelName,
        }) ??
        false;
  }

  Future<List<Map<String, dynamic>>> getLastCtripRooms() async {
    final result = await _channel.invokeListMethod<dynamic>(
      'getLastCtripRooms',
    );
    return (result ?? const <dynamic>[])
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }

  Future<Map<String, dynamic>?> getLastCtripCapture() async {
    final result = await _channel.invokeMapMethod<dynamic, dynamic>(
      'getLastCtripCapture',
    );
    if (result == null || result.isEmpty) return null;
    return Map<String, dynamic>.from(result);
  }

  Future<bool> startCtripBatch(List<Map<String, String>> tasks) async {
    return await _channel.invokeMethod<bool>('startCtripBatch', {
          'tasks': tasks,
        }) ??
        false;
  }

  Future<List<Map<String, dynamic>>> getBatchCtripCaptures() async {
    final result = await _channel.invokeListMethod<dynamic>(
      'getBatchCtripCaptures',
    );
    return (result ?? const <dynamic>[])
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }

  Future<Map<String, dynamic>?> captureCurrentMeituanDetail(
    String hotelName,
  ) async {
    final result = await _channel.invokeMapMethod<dynamic, dynamic>(
      'captureCurrentMeituanDetail',
      {'hotelName': hotelName},
    );
    if (result == null || result.isEmpty) return null;
    return Map<String, dynamic>.from(result);
  }

  Future<bool> startMeituanHotel(String hotelName) async {
    return await _channel.invokeMethod<bool>('startMeituanHotel', {
          'hotelName': hotelName,
        }) ??
        false;
  }

  Future<Map<String, dynamic>?> getLastMeituanCapture() async {
    final result = await _channel.invokeMapMethod<dynamic, dynamic>(
      'getLastMeituanCapture',
    );
    if (result == null || result.isEmpty) return null;
    return Map<String, dynamic>.from(result);
  }

  Future<bool> startMeituanBatch(List<Map<String, String>> tasks) async {
    return await _channel.invokeMethod<bool>('startMeituanBatch', {
          'tasks': tasks,
        }) ??
        false;
  }

  Future<bool> startCombinedBatch({
    required List<Map<String, String>> ctripTasks,
    required List<Map<String, String>> meituanTasks,
  }) async {
    return await _channel.invokeMethod<bool>('startCombinedBatch', {
          'ctripTasks': ctripTasks,
          'meituanTasks': meituanTasks,
        }) ??
        false;
  }

  Future<List<Map<String, dynamic>>> getBatchMeituanCaptures() async {
    final result = await _channel.invokeListMethod<dynamic>(
      'getBatchMeituanCaptures',
    );
    return (result ?? const <dynamic>[])
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }
}
