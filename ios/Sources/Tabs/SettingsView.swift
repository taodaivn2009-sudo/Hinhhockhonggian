import SwiftUI

struct SettingsView: View {
    @ObservedObject var aiStore: AiAssistantStore
    @State private var apiKeyInput = ""
    @State private var baseUrlInput = ""
    @State private var modelInput = ""
    @State private var updateStatus: String? = nil
    @State private var latestReleaseUrl: String? = nil
    @State private var isCheckingUpdate = false

    private var appVersionTag: String {
        (Bundle.main.object(forInfoDictionaryKey: "ToolToanVersionTag") as? String)
            ?? (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String)
            ?? "v1"
    }

    private var githubRepo: String {
        (Bundle.main.object(forInfoDictionaryKey: "ToolToanGithubRepo") as? String) ?? ""
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cài đặt Tool Toán iOS")
                            .font(.system(size: 20, weight: .heavy))
                            .foregroundColor(AppTheme.textPrimary)
                        Text("Phiên bản \(appVersionTag) · Tương thích iOS 15+")
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.textSecondary)
                    }
                    Spacer()
                }
                .padding(.top, 8)

                // Provider Selector
                VStack(alignment: .leading, spacing: 10) {
                    Text("Nguồn AI & Streaming thời gian thực")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(AppTheme.cyan)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(AiProviderKind.allCases) { p in
                                let selected = (aiStore.activeProvider == p)
                                Button(action: {
                                    aiStore.activeProvider = p
                                    syncInputsFromStore()
                                }) {
                                    Text(p.title)
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(selected ? .black : AppTheme.textPrimary)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(selected ? AppTheme.cyan : AppTheme.slateDeepest.opacity(0.7))
                                        .clipShape(Capsule())
                                        .overlay(
                                            Capsule().stroke(selected ? AppTheme.cyan : AppTheme.slateBorder, lineWidth: 1)
                                        )
                                }
                            }
                        }
                    }

                    if aiStore.activeProvider == .customOpenAi {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Base URL (OpenAI-compatible)")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(AppTheme.textSecondary)
                            TextField("https://your-proxy.example/v1", text: $baseUrlInput)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .autocapitalization(.none)
                                .disableAutocorrection(true)
                                .onChange(of: baseUrlInput) { val in
                                    aiStore.updateActiveConfig { $0.baseUrl = val }
                                }
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("API Key (\(aiStore.activeProvider.title))")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(AppTheme.textSecondary)
                        SecureField("Dán API Key vào đây...", text: $apiKeyInput)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                            .onChange(of: apiKeyInput) { val in
                                aiStore.updateActiveConfig { $0.apiKey = val }
                            }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Mã mô hình (Model ID)")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(AppTheme.textSecondary)
                        TextField(aiStore.activeProvider.defaultModelId, text: $modelInput)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                            .onChange(of: modelInput) { val in
                                aiStore.updateActiveConfig { $0.modelId = val }
                            }
                    }

                    // Model chips (synced + presets)
                    let availableModels = aiStore.activeConfig.syncedModels.isEmpty
                        ? aiStore.activeProvider.presetModels
                        : aiStore.activeConfig.syncedModels
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(availableModels.prefix(20), id: \.self) { m in
                                let active = (aiStore.activeModelId == m)
                                Button(action: {
                                    modelInput = m
                                    aiStore.updateActiveConfig { $0.modelId = m }
                                }) {
                                    Text(m)
                                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                        .foregroundColor(active ? AppTheme.emerald : AppTheme.textSecondary)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(active ? AppTheme.emerald.opacity(0.18) : AppTheme.slateDeepest.opacity(0.6))
                                        .cornerRadius(10)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10)
                                                .stroke(active ? AppTheme.emerald : AppTheme.slateBorder, lineWidth: 1)
                                        )
                                }
                            }
                        }
                    }

                    Button(action: { aiStore.syncModels() }) {
                        HStack(spacing: 8) {
                            if aiStore.isSyncingModels {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .black))
                            } else {
                                Image(systemName: "arrow.triangle.2.circlepath")
                            }
                            Text("Đồng bộ mô hình & Kiểm tra Key")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(AppTheme.cyan)
                        .cornerRadius(12)
                    }
                    .disabled(aiStore.isSyncingModels)

                    if let msg = aiStore.connectionStatusMessage {
                        Text(msg)
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.textSecondary)
                    }
                }
                .padding(14)
                .liquidGlassCard(cornerRadius: 18, borderColor: AppTheme.cyan.opacity(0.4))

                // Updates & iPhone IPA install info
                VStack(alignment: .leading, spacing: 10) {
                    Text("Cập nhật bản iPhone (.ipa) từ GitHub")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(AppTheme.emerald)

                    Text("Mỗi bản phát hành trên GitHub đều tự động biên dịch sẵn tệp .ipa không ký (hỗ trợ mọi iPhone từ iOS 15 trở lên qua AltStore, SideStore, Sideloadly, TrollStore, ESign).")
                        .font(.system(size: 12))
                        .foregroundColor(AppTheme.textSecondary)

                    Button(action: { checkGithubUpdates() }) {
                        HStack(spacing: 8) {
                            if isCheckingUpdate {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .black))
                            } else {
                                Image(systemName: "arrow.down.circle.fill")
                            }
                            Text("Kiểm tra bản cập nhật .ipa mới nhất")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(AppTheme.emerald)
                        .cornerRadius(12)
                    }
                    .disabled(isCheckingUpdate)

                    if let status = updateStatus {
                        Text(status)
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.textPrimary)
                    }

                    if let urlStr = latestReleaseUrl, let url = URL(string: urlStr) {
                        Button(action: { UIApplication.shared.open(url) }) {
                            Text("Mở trang tải file .ipa trên GitHub")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(AppTheme.cyan)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(AppTheme.cyan.opacity(0.14))
                                .cornerRadius(12)
                        }
                    }
                }
                .padding(14)
                .liquidGlassCard(cornerRadius: 18, borderColor: AppTheme.emerald.opacity(0.4))
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 100)
        }
        .onAppear { syncInputsFromStore() }
    }

    private func syncInputsFromStore() {
        let cfg = aiStore.activeConfig
        apiKeyInput = cfg.apiKey
        baseUrlInput = cfg.baseUrl
        modelInput = cfg.modelId.isEmpty ? aiStore.activeProvider.defaultModelId : cfg.modelId
    }

    private func checkGithubUpdates() {
        guard !githubRepo.isEmpty,
              let url = URL(string: "https://api.github.com/repos/\(githubRepo)/releases/latest") else {
            updateStatus = "Phiên bản hiện tại: \(appVersionTag)."
            return
        }
        isCheckingUpdate = true
        updateStatus = "Đang kiểm tra GitHub Releases..."
        Task {
            defer { isCheckingUpdate = false }
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let tag = obj["tag_name"] as? String {
                    let htmlUrl = obj["html_url"] as? String
                    let assets = obj["assets"] as? [[String: Any]] ?? []
                    let ipaUrl = assets.first(where: { ($0["name"] as? String)?.hasSuffix(".ipa") == true })?["browser_download_url"] as? String
                    latestReleaseUrl = ipaUrl ?? htmlUrl
                    if tag == appVersionTag {
                        updateStatus = "Bạn đang dùng bản mới nhất (\(tag))."
                    } else {
                        updateStatus = "Đã có phiên bản mới \(tag) (hiện tại: \(appVersionTag))."
                    }
                }
            } catch {
                updateStatus = "Không kiểm tra được cập nhật: \(error.localizedDescription)"
            }
        }
    }
}
