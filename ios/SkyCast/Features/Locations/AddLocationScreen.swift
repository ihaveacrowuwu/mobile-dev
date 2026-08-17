import SwiftUI

/// Search OpenWeather's geocoder and save a place.
///
/// Uses `.searchable`, not a hand-built text field: it supplies the platform search bar, the
/// clear button, keyboard handling, the scroll-to-dismiss behaviour and correct VoiceOver
/// semantics. A custom field would reimplement all of that and get some of it wrong.
struct AddLocationScreen: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss

    @State private var viewModel: AddLocationViewModel?
    @State private var query = ""

    var body: some View {
        Group {
            if let viewModel {
                content(for: viewModel)
            } else {
                LoadingView()
            }
        }
        .navigationTitle("Add location")
        .searchable(
            text: $query,
            placement: .navigationBarDrawer(displayMode: .always),
            prompt: "City name, e.g. London"
        )
        .onChange(of: query) { _, newValue in
            viewModel?.onQueryChange(newValue)
        }
        .task {
            if viewModel == nil {
                viewModel = AddLocationViewModel(locationRepository: container.locationRepository)
            }
        }
    }

    @ViewBuilder
    private func content(for viewModel: AddLocationViewModel) -> some View {
        let state = viewModel.state

        Group {
            if state.isSearching {
                ProgressView()
                    .controlSize(.large)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error = state.error {
                ErrorView(error: error)
            } else if state.showsPrompt {
                CentredHint("Type at least two letters to search.")
            } else if state.showsNoResults {
                CentredHint("No places found for “\(state.query.trimmingCharacters(in: .whitespaces))”.")
            } else {
                List(state.results) { result in
                    Button {
                        Task {
                            await viewModel.save(result)
                            // Pop on success so the user lands back on the list that now contains
                            // what they just added, rather than pressing back and wondering.
                            if viewModel.state.didSave {
                                dismiss()
                            }
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: Spacing.xxs) {
                            Text(result.name)
                                .font(.body)
                                .foregroundStyle(.primary)
                            Text(result.displayName)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityLabel("Add \(result.displayName)")
                }
            }
        }
    }
}

/// Centred hint text, shared by the prompt and no-results states.
private struct CentredHint: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    NavigationStack {
        AddLocationScreen()
            .environment(AppContainer.preview())
    }
}
