"""Registry of the public datasets used for training (source, version, checksum)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = REPO_ROOT / "data" / "raw" / "lds"
PROCESSED_DIR = REPO_ROOT / "data" / "processed"
PROCESSED_FILE = PROCESSED_DIR / "lds_wheat_rice_2018.csv"


@dataclass(frozen=True)
class Dataset:
    key: str
    crop: str
    title: str
    handle: str
    dataverse_version: str
    released: str
    file_id: int
    filename: str
    md5: str
    paper: str
    licence: str

    @property
    def url(self) -> str:
        return f"https://data.cimmyt.org/api/access/datafile/{self.file_id}"

    @property
    def path(self) -> Path:
        return RAW_DIR / self.filename


LDS_WHEAT = Dataset(
    key="lds_wheat_2018",
    crop="WHEAT",
    title="Landscape diagnostic survey data of wheat production practices and yield of 2018 from eastern India",
    handle="hdl:11529/10548507",
    dataverse_version="2.0",
    released="2024-01-30",
    file_id=58463,
    filename="CSISA_IND_LDS_Whe_2018_Data.csv",
    md5="26b075c43ab9f84fc9520e82e11e612f",
    paper="Ajay et al., Open Data Journal for Agricultural Research, doi:10.18174/odjar.v7i0.17959",
    licence="Data paper CC BY 4.0; Dataverse record states CIMMYT policy (cite, contact authors for other uses)",
)

LDS_RICE = Dataset(
    key="lds_rice_2018",
    crop="RICE",
    title="Large-scale data of crop production practices applied by farmers on their largest rice plot during "
          "2018 in eight Indian states",
    handle="hdl:11529/10548656",
    dataverse_version="3.0",
    released="2022-08-26",
    file_id=20843,
    filename="CSISA_IND_LDS_Rice_2018_Data.csv",
    md5="d61e6fe94d20e5a8362d1d9943c8fdb8",
    paper="Ajay et al. (2022), Data in Brief, PMC9679526 (CC BY 4.0)",
    licence="Data paper CC BY 4.0; Dataverse record states CIMMYT policy (cite, contact authors for other uses)",
)

DATASETS = (LDS_WHEAT, LDS_RICE)
