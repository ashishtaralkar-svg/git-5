// Minimal IndexedDB wrapper for persisting documents (blobs + metadata).
// Mirrors the Room schema of the Android app: pending vs uploaded documents,
// keyed by application number.

const DB_NAME = 'docupload';
const DB_VERSION = 1;
const STORE = 'documents';

function openDb() {
    return new Promise((resolve, reject) => {
        const req = indexedDB.open(DB_NAME, DB_VERSION);
        req.onupgradeneeded = () => {
            const db = req.result;
            if (!db.objectStoreNames.contains(STORE)) {
                const store = db.createObjectStore(STORE, { keyPath: 'id', autoIncrement: true });
                store.createIndex('appNo', 'appNo', { unique: false });
                store.createIndex('appNo_status', ['appNo', 'status'], { unique: false });
            }
        };
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
    });
}

function tx(db, mode) {
    return db.transaction(STORE, mode).objectStore(STORE);
}

export async function addDocument(doc) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const req = tx(db, 'readwrite').add({
            appNo: doc.appNo,
            name: doc.name,
            mime: doc.mime,
            size: doc.size,
            type: doc.type,            // 'image' | 'pdf' | 'scan'
            status: doc.status || 'pending',
            createdAt: doc.createdAt || Date.now(),
            blob: doc.blob,
        });
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
    });
}

export async function deleteDocument(id) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const req = tx(db, 'readwrite').delete(id);
        req.onsuccess = () => resolve();
        req.onerror = () => reject(req.error);
    });
}

export async function updateStatus(id, status) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const store = tx(db, 'readwrite');
        const getReq = store.get(id);
        getReq.onsuccess = () => {
            const rec = getReq.result;
            if (!rec) return resolve();
            rec.status = status;
            const putReq = store.put(rec);
            putReq.onsuccess = () => resolve();
            putReq.onerror = () => reject(putReq.error);
        };
        getReq.onerror = () => reject(getReq.error);
    });
}

export async function listByStatus(appNo, status) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const index = tx(db, 'readonly').index('appNo_status');
        const req = index.getAll(IDBKeyRange.only([appNo, status]));
        req.onsuccess = () => {
            const rows = (req.result || []).sort((a, b) =>
                status === 'uploaded' ? b.createdAt - a.createdAt : a.createdAt - b.createdAt);
            resolve(rows);
        };
        req.onerror = () => reject(req.error);
    });
}

export const listPending = (appNo) => listByStatus(appNo, 'pending');
export const listUploaded = (appNo) => listByStatus(appNo, 'uploaded');
