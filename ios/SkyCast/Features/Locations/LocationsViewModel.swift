import Foundation
import Observation

/// State for the saved-locations list.
struct LocationsUiState: Equatable {
    var locations: [SavedLocation] = []
    var isLoading = true
    var error: AppError?

    var isEmpty: Bool {
        locations.isEmpty && !isLoading
    }

    /// Whether there is room for another place.
    ///
    /// Surfaced so the Add button can go quiet at the cap instead of leading to a search that can only
    /// end in an error. The repository still enforces the limit, this is the courtesy, not the rule.
    var canAddMore: Bool {
        SavedLocation.canSaveAnother(currentCount: locations.count)
    }

    /// The last remaining location cannot be removed: with none saved, Home has nothing to show
    /// and the user is stranded on an empty state they did not ask for.
    var canDelete: Bool {
        locations.count > 1
    }
}

/// The Locations tab.
///
/// Reloads after every mutation rather than observing a stream: SwiftData has no direct equivalent
/// of Room's `Flow<List<T>>` here, since `@Query` only works inside a `View`, and this view model
/// owns its state.
@MainActor
@Observable
final class LocationsViewModel {
    private(set) var state = LocationsUiState()

    private let locationRepository: any LocationRepository

    init(locationRepository: any LocationRepository) {
        self.locationRepository = locationRepository
    }

    func load() async {
        do {
            state.locations = try await locationRepository.savedLocations()
            state.error = nil
        } catch {
            state.error = AppError.from(error)
        }
        state.isLoading = false
    }

    func setPrimary(_ location: SavedLocation) async {
        try? await locationRepository.setPrimary(location)
        await load()
    }

    func delete(_ location: SavedLocation) async {
        guard state.canDelete else { return }
        try? await locationRepository.delete(location)
        await load()
    }
}

/// State for the search-and-add screen.
struct AddLocationUiState: Equatable {
    var query = ""
    var results: [LocationSearchResult] = []
    var isSearching = false
    var error: AppError?
    var didSave = false

    /// "No matches" is only meaningful once a real query has actually run.
    var showsNoResults: Bool {
        results.isEmpty && !isSearching && error == nil
            && query.trimmingCharacters(in: .whitespaces).count >= Self.minimumQuery
    }

    var showsPrompt: Bool {
        query.trimmingCharacters(in: .whitespaces).count < Self.minimumQuery && results.isEmpty
    }

    /// Matches the guard in `LocationRepositoryImpl.search`.
    static let minimumQuery = 2
}

/// Search OpenWeather's geocoder and save a place.
///
/// The query is **debounced** before it reaches the network. The free tier allows 60 calls a
/// minute; searching on every keystroke would exhaust that in seconds, so this is a quota
/// constraint rather than a nicety.
@MainActor
@Observable
final class AddLocationViewModel {
    private(set) var state = AddLocationUiState()

    private let locationRepository: any LocationRepository
    private var searchTask: Task<Void, Never>?

    init(locationRepository: any LocationRepository) {
        self.locationRepository = locationRepository
    }

    func onQueryChange(_ newQuery: String) {
        state.query = newQuery
        // Clear a stale error as soon as the user edits, so the message never outlives its cause.
        state.error = nil

        let trimmed = newQuery.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= AddLocationUiState.minimumQuery else {
            searchTask?.cancel()
            state.results = []
            state.isSearching = false
            return
        }

        // Cancelling the previous task is the debounce: each keystroke restarts the wait, so only
        // the last one in a burst survives to make a request.
        searchTask?.cancel()
        searchTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(Self.searchDebounceMilliseconds))
            guard !Task.isCancelled else { return }
            await self?.runSearch(trimmed)
        }
    }

    func save(_ result: LocationSearchResult) async {
        do {
            try await locationRepository.save(result)
            state.didSave = true
        } catch {
            state.error = AppError.from(error)
        }
    }

    private func runSearch(_ query: String) async {
        state.isSearching = true
        do {
            state.results = try await locationRepository.search(query: query)
            state.error = nil
        } catch {
            state.results = []
            state.error = AppError.from(error)
        }
        state.isSearching = false
    }

    /// 400 ms: long enough that a fast typist triggers one request rather than eight, short
    /// enough that the list still feels responsive. Matches Android's `SEARCH_DEBOUNCE_MILLIS`.
    ///
    /// `static` so the debounce `Task` can read it without capturing `self`.
    private static let searchDebounceMilliseconds = 400
}
