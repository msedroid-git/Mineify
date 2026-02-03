import youtubeSearch from 'youtube-search-api';

export async function search(query, maxResults = 10) {
    const results = await youtubeSearch.GetListByKeyword(query, false, maxResults);
    return results.items
        .filter(item => item.type === 'video')
        .map(item => ({
            videoId: item.id,
            title: item.title,
            channel: item.channelTitle || '',
            duration: item.length?.simpleText || '',
            thumbnail: item.thumbnail?.thumbnails?.[0]?.url || ''
        }));
}
